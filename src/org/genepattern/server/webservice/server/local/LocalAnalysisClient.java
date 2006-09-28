/*
 The Broad Institute
 SOFTWARE COPYRIGHT NOTICE AGREEMENT
 This software and its documentation are copyright (2003-2006) by the
 Broad Institute/Massachusetts Institute of Technology. All rights are
 reserved.

 This software is supplied without any warranty or guaranteed support
 whatsoever. Neither the Broad Institute nor MIT can be responsible for its
 use, misuse, or functionality.
 */

package org.genepattern.server.webservice.server.local;

import org.genepattern.server.database.HibernateUtil;
import org.genepattern.server.handler.AddNewJobHandler;
import org.genepattern.server.webservice.server.Analysis;

import org.genepattern.webservice.*;
import java.util.Map;

/**
 * local Analysis client
 * 
 * @author Joshua Gould
 */
public class LocalAnalysisClient {
    Analysis service;
    String userName;

    public LocalAnalysisClient(final String userName) {
        this.userName = userName;
        service = new Analysis() {
            protected String getUsernameFromContext() {
                return userName;
            }
        };
    }

    public void deleteJob(int jobId) throws WebServiceException {
        service.deleteJob(jobId);
    }

    public void deleteJobResultFile(int jobId, String value) throws WebServiceException {
        service.deleteJobResultFile(jobId, value);
    }

    public void terminateJob(int jobId) throws WebServiceException {
        service.terminateJob(jobId);
    }

    public JobInfo[] getJobs(String username, int maxJobNumber, int maxEntries, boolean all) throws WebServiceException {
        return service.getJobs(username, maxJobNumber, maxEntries, all);
    }

    public JobInfo getJob(int jobId) throws WebServiceException {

        return service.getJob(jobId);
    }

    public String createProvenancePipeline(JobInfo[] jobs, String pipelineName) {
        return service.createProvenancePipeline(jobs, pipelineName);

    }

    public String createProvenancePipeline(String fileUrlOrJobNumber, String pipelineName) {
        return service.createProvenancePipeline(fileUrlOrJobNumber, pipelineName);

    }

    public JobInfo[] findJobsThatCreatedFile(String fileURLOrJobNumber) {
        return service.findJobsThatCreatedFile(fileURLOrJobNumber);

    }

    public JobInfo[] getChildren(int jobNumber) throws WebServiceException {
        int[] children = service.getChildren(jobNumber);
        JobInfo[] childJobs = new JobInfo[children.length];

        for (int i = 0, length = children.length; i < length; i++) {
            childJobs[i] = service.getJob(children[i]);
        }
        return childJobs;
    }

    // XXX Where should files be located?
    public JobInfo submitJob(int taskID, ParameterInfo[] parameters) throws WebServiceException {

        return service.submitLocalJob(taskID, parameters);

    }

    public JobInfo submitJob(int taskID, ParameterInfo[] parameters, Map files) throws WebServiceException {
        return service.submitJob(taskID, parameters, files);
    }

    /**
     * Check the status of the current job.  This method is called repeatedly
     * from the run task jsp until the job status changes to "finished".  
     * It is wrapped in its own transaction to (a) release database resources
     * between calls, and (b) insure that the latest status is read.  
     * (An alternative solution to (b) would be to call session.clear();
     *
     * @param jobID  a job number
     * @return
     * @throws WebServiceException
     */
    public JobInfo checkStatus(int jobID) throws WebServiceException {
        try {
            HibernateUtil.commitTransaction();
            HibernateUtil.beginTransaction();

            JobInfo j = service.checkStatus(jobID);
            
            HibernateUtil.commitTransaction();
            return j;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new WebServiceException(e);
        }
        finally {
            HibernateUtil.closeCurrentSession();
        }
    }

}
