package org.genepattern.server.executor.lsf;

import java.io.File;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import org.apache.log4j.Logger;
import org.genepattern.server.domain.JobStatus;
import org.genepattern.server.executor.CommandManagerFactory;
import org.genepattern.server.genepattern.GenePatternAnalysisTask;
import org.genepattern.server.webservice.server.dao.AnalysisDAO;
import org.genepattern.webservice.JobInfo;

import edu.mit.broad.core.lsf.LsfJob;
import edu.mit.broad.core.lsf.LsfJob.JobCompletionListener;

/**
 * Handle job completion events from the BroadCore LSF handler.
 * @author pcarr
 */
public class LsfJobCompletionListener implements JobCompletionListener {
    private static Logger log = Logger.getLogger(LsfJobCompletionListener.class);
    private static ExecutorService executor = Executors.newFixedThreadPool(3);
    
    /**
     * Note: using the JOB_LSF.NAME column for storing the GP_JOB_ID
     * @param job
     * @return
     * @throws Exception
     */
    public static int getGpJobId(LsfJob job) throws Exception {
        if (job == null) throw new Exception("Null arg");
        if (job.getName() == null) throw new Exception("Null job.name");
        String jobIdStr = job.getName();
        return Integer.parseInt(jobIdStr);
    }

    private static String getOutputFilename(int gpJobId, LsfJob job) {
        String stdoutFilename = job.getOutputFilename();

        AnalysisDAO dao = new AnalysisDAO();
        JobInfo jobInfo = dao.getJobInfo(gpJobId);
        Properties lsfProperties = CommandManagerFactory.getCommandManager().getCommandProperties(jobInfo);
        String wrapperScript = lsfProperties.getProperty(LsfProperties.Key.WRAPPER_SCRIPT.getKey());
        if (wrapperScript != null) {
            String commandLine = job.getCommand();
            List<String> commandLineArgs = StringUtil.splitCommandLine(commandLine);
            if (commandLineArgs.size() >= 2) {
                String arg0 = commandLineArgs.get(0);
                String arg1 = commandLineArgs.get(1);
                if (wrapperScript.equals(arg0)) {
                    stdoutFilename = arg1;
                }
            }
        }
        
        log.debug("computed output file name is: "+stdoutFilename);
        //NOTE: this code is buggy, at the moment we would be deleting the wrapper script ...
        //    TODO: set up user account so that the gpdev account running the server cannot delete the wrapper scripts and other 
        //        server files
        //    TODO: fix the bug so that the correct output file name is detected when a wrapper script is being used
        log.debug("using 'stdout.txt' instead!");
        //TODO: fix this!
        stdoutFilename = "stdout.txt";
        
        //special-case, delete the stdoutFile if it is empty
        //TODO: this should be part of the GenePatternAnalysisTask#handleJobCompletion method
        File stdoutFile = new File(stdoutFilename);
        if (stdoutFile.getParent() == null) {
            stdoutFile = new File(job.getWorkingDirectory(), stdoutFilename);
        }
        if (stdoutFile.canWrite() && stdoutFile.length() <= 0L) {
            boolean deleted = stdoutFile.delete();
            if (deleted) {
                log.debug("deleted empty stdout file: "+stdoutFile.getAbsolutePath());
            }
            else {
                log.error("unable to delete empty stdout file: "+stdoutFile.getAbsolutePath());
            }
        }
        
        return stdoutFilename;
    }

    public void jobCompleted(final LsfJob job) throws Exception {
        final int gpJobId = getGpJobId(job);
        //TODO: check for error or terminated status
        log.debug("job completed...lsf_id="+job.getLsfJobId()+", internal_job_id="+job.getInternalJobId()+", gp_job_id="+gpJobId);
        
        final String stderrFilename = job.getErrorFileName();
        //TODO: get the exit code from the lsf job and send it along to genepattern
        final int exitCode = 0;

        //must run this in a new thread because this callback is run from within a hibernate transaction
        //and the GenePatternAnalyisTask.handleJobCompletion closes that transaction
        //See the comments in GenePatternAnalysisTask to see why the transaction must be closed, it is
        //    related to Oracle CLOBs.
        FutureTask<Integer> future =
            new FutureTask<Integer>(new Callable<Integer>() {
              public Integer call() throws Exception {
                  //special handling for stdoutFilename as it could be the case the the bsub -o arg is a different file than the stdout from the gp command
                  String stdoutFilename = job.getOutputFilename();
                  try {
                      //Note: this method opens a db connection, which is closed in the call to handleJobCompletion
                      stdoutFilename = getOutputFilename(gpJobId, job);
                  }
                  catch (Throwable t) {
                      log.error("Error getting stdout filename for LSF job, using the lsf output filename instead: "+stdoutFilename, t);
                  }
                  int rVal = 0;
                  int gpJobStatus = JobStatus.JOB_FINISHED;
                  if (stderrFilename != null) {
                      File errFile = new File(stderrFilename);
                      if (errFile.getParent() == null) {
                          errFile = new File(job.getWorkingDirectory(), stderrFilename);
                      }
                      if (errFile.exists() && errFile.length() > 0L) {
                          gpJobStatus = JobStatus.JOB_ERROR;
                      }
                  }
                  GenePatternAnalysisTask.handleJobCompletion(gpJobId, stdoutFilename, stderrFilename, exitCode, gpJobStatus);
                  return rVal;
            }});
          executor.execute(future);

          //wait for the thread to complete before exiting
          try {
              int statusCode = future.get();
              log.debug("job #"+gpJobId+" saved to GP database, statusCode="+statusCode);
          }
          catch (Throwable t) {
              String message = "Error handling job completion for job #"+gpJobId;
              log.error(message,t);
              throw new Exception(message, t);
          }
    }
}
