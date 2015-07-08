/*******************************************************************************
 * Copyright (c) 2003, 2015 Broad Institute, Inc. and Massachusetts Institute of Technology.  All rights reserved.
 *******************************************************************************/
package org.genepattern.server.database;

import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.Logger;
import org.genepattern.server.DbException;
import org.genepattern.server.config.GpConfig;
import org.genepattern.server.domain.PropsTable;
import org.genepattern.webservice.OmnigeneException;

import com.google.common.base.Strings;

/**
 * Automatically update the GenePattern database schema, usually called on server startup.
 * 
 * This is a re-factor of code from the HsqlDbUtil class circa GP <= 3.9.2 which loads 
 * matching database DDL scripts (analysis_<db_type>-<gp-version>.sql) from the resources directory.
 * 
 *  The main difference is that a HibernateSessionManager is passed as an argument to the helper methods
 *  to make it easier to test. 
 * 
 * @author pcarr
 *
 */
public class SchemaUpdater {
    private static final Logger log = Logger.getLogger(SchemaUpdater.class);

    public static void updateSchema(final GpConfig gpConfig, final HibernateSessionManager mgr) 
    throws DbException
    {
        final File schemaDir=new File(gpConfig.getWebappDir(), "WEB-INF/schema");
        SchemaUpdater.updateSchema(mgr, schemaDir, gpConfig.getDbSchemaPrefix()); 
    }
    
    public static void updateSchema(final HibernateSessionManager mgr, final File schemaDir, final String schemaPrefix) 
    throws DbException
    {
        // by default, set the expected schema version to the latest one in the list
        updateSchema(mgr, schemaDir, schemaPrefix, null);
    }
    
    public static void updateSchema(final HibernateSessionManager mgr, final File resourceDir, final String schemaPrefix, final String expectedSchemaVersion) 
    throws DbException
    {
        try {
            mgr.beginTransaction();
        }
        catch (Throwable t) {
            // ... 1) can't even begin a transaction
            throw new DbException("Database connection error: "+t.getLocalizedMessage(), t);
        }
        try {
            innerUpdateSchema(mgr, resourceDir, schemaPrefix, expectedSchemaVersion);
            mgr.commitTransaction();
        }
        catch (DbException e) {
            mgr.rollbackTransaction();
            throw e;
        }
        catch (Throwable t) {
            mgr.rollbackTransaction();
            throw new DbException("Unexpected error initializing the databse: "+t.getLocalizedMessage(), t);
        }
        finally {
            try {
                mgr.closeCurrentSession();
            }
            catch (Exception e) {
                log.error("Exception thrown closing database connection: "+e.getLocalizedMessage(), e);
            }
        }
    }
    
    /**
     * 
     * Note: this method assumes that the schemaFiles were initialized using the same exact expectedSchemaVersion 
     * as passed into this method. We don't double-check this here.
     * 
     * @param schemaFiles
     * @param schemaPrefix
     * @param expectedSchemaVersion
     * @return
     */
    protected static boolean isUpToDate(final List<File> schemaFiles) {
        if (schemaFiles==null) {
            log.warn("schemaFiles==null, assuming schema up-to-date");
            return true;
        }
        else if (schemaFiles.size()==0) {
            log.debug("schemaFiles.size()==0, assuming schema up-to-date");
            return true;
        }
        return false;
    }
    
    private static void innerUpdateSchema(final HibernateSessionManager sessionMgr, final File resourceDir, final String schemaPrefix, final String expectedSchemaVersion) 
    throws DbException 
    {
        final String dbSchemaVersion=getDbSchemaVersion(sessionMgr);
        final List<File> schemaFiles=SchemaUpdater.listSchemaFiles(resourceDir, schemaPrefix, expectedSchemaVersion, dbSchemaVersion);
        boolean upToDate=isUpToDate(schemaFiles);
        log.info("schema up-to-date=" + upToDate + ", expectedSchemaVersion=" + expectedSchemaVersion + ", current dbSchemaVersion=" + dbSchemaVersion);
        if (upToDate) {
            return;
        }
        
        String proposedSchemaVersion=""; 
        if (schemaFiles != null && schemaFiles.size() > 0) {
            File lastSchemaFile=schemaFiles.get( schemaFiles.size()-1 );
            proposedSchemaVersion=DbSchemaFilter.initSchemaVersionFromFilename(schemaPrefix, lastSchemaFile);
        }

        // run new DDL scripts to bring the DB up to date with the proposed schema version
        log.info("Updating schema from "+dbSchemaVersion+" to "+proposedSchemaVersion+" ...");        
        createSchema(sessionMgr, schemaFiles);

        // validate that the DB version is up to date
        final String actualSchemaVersion=getDbSchemaVersion(sessionMgr);
        if (!Strings.isNullOrEmpty(actualSchemaVersion) && !Strings.isNullOrEmpty(proposedSchemaVersion)) {
            upToDate = (proposedSchemaVersion.compareTo(actualSchemaVersion) <= 0);
        }
        if (!upToDate) {
            log.error("schema didn't have correct version after creating");
            throw new DbException("schema didn't have correct version after creating, updateToSchemaVersion="+proposedSchemaVersion+", actual="+actualSchemaVersion);
        }
        log.info("Updating schema...Done!");
    }

    /**
     * Get the list of schema files to process for the given schemaPrefix, e
     * @return
     */
    protected static List<File> listSchemaFiles(final File schemaDir, final String schemaPrefix, final String expectedSchemaVersion, final String dbSchemaVersion) {
        log.debug("listing schema files ... ");
        List<File> rval=new ArrayList<File>();
        final DbSchemaFilter schemaFilenameFilter = new DbSchemaFilter.Builder()
            .schemaPrefix(schemaPrefix)
            .dbSchemaVersion(dbSchemaVersion)
            .maxSchemaVersion(expectedSchemaVersion)
        .build();
        File[] schemaFiles = schemaDir.listFiles(schemaFilenameFilter);
        Arrays.sort(schemaFiles, schemaFilenameFilter);
        for (int f = 0; f < schemaFiles.length; f++) {
            final File schemaFile = schemaFiles[f];
            final String name=schemaFile.getName();
            final String schemaVersion=schemaFilenameFilter.initSchemaVersion(schemaFile);
            if (schemaFilenameFilter.acceptSchemaVersion(schemaVersion)) {
                log.debug("adding " + name + " (" + schemaVersion + ")");
                rval.add(schemaFile);
            }
            else {
                log.debug("skipping " + name + " (" + schemaVersion + ")");
            }
        }
        log.debug("listing schema files ... Done!");
        return rval;
    }

    /**
     * Query the database for the current schema version recorded in the database.
     * @return
     */
    protected static String getDbSchemaVersion(final HibernateSessionManager sessionMgr) throws DbException {
        String dbSchemaVersion="";
        final boolean isInTransaction=sessionMgr.isInTransaction();
        try {
            if (!isInTransaction) {
                sessionMgr.beginTransaction();
            }

            boolean e=tableExists(sessionMgr, "PROPS");
            if (!e) {
                e=tableExists(sessionMgr, "props");
            }
            if (!e) {
                log.info("No 'props' table in database. Create new database.");
                dbSchemaVersion="";
            }
            else {
                dbSchemaVersion=PropsTable.selectValue(sessionMgr, "schemaVersion");
            }
        }
        catch (Throwable t) {
            throw new DbException("Unexpected error getting dbSchemaVersion: "+t.getLocalizedMessage(), t);
        }
        finally {
            if (!isInTransaction) {
                sessionMgr.closeCurrentSession();
            }
        }
        log.info("Current dbSchemaVersion: "+dbSchemaVersion);
        return dbSchemaVersion;
    }

    protected static boolean tableExists(final HibernateSessionManager sessionMgr, String tableName) {
        final boolean isInTransaction=sessionMgr.isInTransaction();
        try {
            sessionMgr.beginTransaction();
            DatabaseMetaData md =
                    sessionMgr.getSession().connection().getMetaData();
            ResultSet rs=md.getTables(null, null, tableName, null);
            if (rs.next()) {
                return true;
            }
        }
        catch (Throwable t) {
            log.error("Unexpected error checking if tableExists for tableName="+tableName, t);
        }
        finally {
            if (!isInTransaction) {
                sessionMgr.closeCurrentSession();
            }
        }
        return false;
    }

    protected static void createSchema(final HibernateSessionManager sessionMgr, final List<File> schemaFiles) 
    throws DbException
    {
        for(final File schemaFile : schemaFiles) {
            processSchemaFile(sessionMgr, schemaFile);
        }
    }

    protected static void processSchemaFile(final HibernateSessionManager sessionMgr, final File schemaFile) throws DbException {
        log.info("updating database from schema " + schemaFile.getPath());
        String all = null;
        try {
            all = HsqlDbUtil.readFile(schemaFile);
        }
        catch (IOException e) {
            log.error("Error reading schema file=" + schemaFile.getPath(), e);
            throw new DbException("Error reading schema file="+schemaFile.getPath(), e);
        }
        while (!all.equals("")) {
            all = all.trim();
            int i = all.indexOf('\n');
            if (i == -1) i = all.length() - 1;
            if (all.startsWith("--")) {
                all = all.substring(i + 1);
                continue;
            }

            i = all.indexOf(';');
            String sql;
            if (i != -1) {
                sql = all.substring(0, i);
                all = all.substring(i + 1);
            }
            else {
                sql = all;
                all = "";
            }
            sql = sql.trim();
            log.info("apply SQL-> " + sql);
            try {
                //BaseDAO dao = new BaseDAO();
                executeUpdate(sessionMgr, sql);
            }
            catch (Throwable t) {
                log.error("Error processing SQL in schemaFile="+schemaFile);
                log.error("sql: "+sql, t);
                throw new DbException("Error processing SQL in schemaFile="+schemaFile+
                        "\n\t"+t.getLocalizedMessage()+
                        "\n\t"+sql);
            }
        }
        log.debug("updating database from schema ... Done!");
    }
    
    /**
     * execute arbitrary SQL on database, returning int
     * 
     * @param sql
     * @throws OmnigeneException
     * @throws RemoteException
     * @return int number of rows returned
     */
    private static int executeUpdate(final HibernateSessionManager sessionMgr, final String sql) throws SQLException 
    {
        sessionMgr.getSession().flush();
        sessionMgr.getSession().clear();
    
        Statement updateStatement = null;
    
        try {
            updateStatement = sessionMgr.getSession().connection().createStatement();
            return updateStatement.executeUpdate(sql);
        }
        finally {
            if (updateStatement != null) {
                updateStatement.close();
            }
        }
    }

}
