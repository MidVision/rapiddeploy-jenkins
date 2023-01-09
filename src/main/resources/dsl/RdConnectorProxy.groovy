package dsl

import org.apache.commons.lang.StringUtils

import com.midvision.rapiddeploy.connector.RapidDeployConnector
import com.midvision.rapiddeploy.plugin.jenkins.RapidDeployConnectorProxy
import com.midvision.rapiddeploy.plugin.jenkins.log.Logger

/**
 * 
 * This class is a code duplication for the same method in the 'RapidDeployConnectorProxy' class.
 * The reason for this is to avoid the exception 'java.lang.InterruptedException: sleep interrupted'
 * using the 'Thread.sleep(milisToSleep)' method in the Java class.
 * Internal ticket: https://support.midvision.com/issues/3782
 *
 */
class RdConnectorProxy {
    static boolean checkJobStatus(final Logger logger, final String serverUrl, final String authenticationToken, final String jobRequestOutput,
            final boolean showIndividualLogs, final boolean showFullLog) throws Exception {

        boolean success = false;
        final String jobId = RapidDeployConnector.extractJobId(jobRequestOutput);
        logger.println(">>>  RapidDeploy job requested [" + jobId + "] <<<");
        if (jobId != null) {
            logger.println("Checking job status every 30 seconds...");
            String jobDetails = "";
            String jobStatus = "";
            boolean runningJob = true;
            long milisToSleep = 30000L;
            while (runningJob) {
                Thread.sleep(milisToSleep);
                jobDetails = RapidDeployConnector.pollRapidDeployJobDetails(authenticationToken, serverUrl, jobId);
                jobStatus = RapidDeployConnector.extractJobStatus(jobDetails);
                logger.println("Job status: " + jobStatus);
                if (jobStatus.equals(RapidDeployConnectorProxy.SUBMITTED) || jobStatus.equals(RapidDeployConnectorProxy.STARTING) || jobStatus.equals(RapidDeployConnectorProxy.EXECUTING) || jobStatus.equals(RapidDeployConnectorProxy.BATCHED)
                || jobStatus.equals(RapidDeployConnectorProxy.RESUMING)) {
                    logger.println("Job running, next check in 30 seconds...");
                    milisToSleep = 30000L;
                } else if (jobStatus.equals(RapidDeployConnectorProxy.REQUESTED) || jobStatus.equals(RapidDeployConnectorProxy.REQUESTED_SCHEDULED) || jobStatus.equals(RapidDeployConnectorProxy.REQUESTED_EXECUTING)) {
                    logger.println("Job in a REQUESTED state. Approval may be required in RapidDeploy "
                            + "to continue with the execution, next check in 30 seconds...");
                } else if (jobStatus.equals(RapidDeployConnectorProxy.JOB_HALTED) || jobStatus.equals(RapidDeployConnectorProxy.TASK_HALTED)) {
                    logger.println("Job in a HALTED state, next check in 5 minutes...");
                    logger.println("Printing out job details: ");
                    logger.println(jobDetails);
                    milisToSleep = 300000L;
                } else if (jobStatus.equals(RapidDeployConnectorProxy.SCHEDULED)) {
                    logger.println("Job in a SCHEDULED state, the execution will start in a future date, next check in 5 minutes...");
                    logger.println("Printing out job details: ");
                    logger.println(jobDetails);
                    milisToSleep = 300000L;
                } else {
                    runningJob = false;
                    logger.println("Job finished with status: " + jobStatus);
                    if (jobStatus.equals(RapidDeployConnectorProxy.COMPLETED)) {
                        success = true;
                    }
                }
            }
            if (showFullLog) {
                final String logs = RapidDeployConnector.pollRapidDeployJobLog(authenticationToken, serverUrl, jobId);
                final StringBuilder fullLog = new StringBuilder();
                fullLog.append(logs);

                if (showIndividualLogs && StringUtils.isNotBlank(jobDetails)) {
                    fullLog.append(System.getProperty("line.separator"));
                    final List<String> includedJobIds = RapidDeployConnector.extractIncludedJobIdsUnderPipelineJob(jobDetails);
                    for (final String internalJobId : includedJobIds) {
                        fullLog.append("Logs related to job ID: ").append(internalJobId).append(System.getProperty("line.separator"));
                        fullLog.append(RapidDeployConnector.pollRapidDeployJobLog(authenticationToken, serverUrl, internalJobId));
                    }
                }

                if (!success) {
                    throw new RuntimeException("RapidDeploy job failed. Please check the output." + System.getProperty("line.separator") + fullLog.toString());
                }
                logger.println("RapidDeploy job successfully run. Please check the output." + System.getProperty("line.separator"));
                logger.println(fullLog.toString());
            } else {
                String baseLogUrl = serverUrl;
                if (serverUrl != null && serverUrl.endsWith("/")) {
                    baseLogUrl = serverUrl.substring(0, serverUrl.length() - 1);
                }
                baseLogUrl = baseLogUrl + "/ws/streamer/job/log/";

                final StringBuilder logUrls = new StringBuilder();
                logUrls.append(baseLogUrl).append(jobId).append(System.getProperty("line.separator"));

                if (showIndividualLogs && StringUtils.isNotBlank(jobDetails)) {
                    logUrls.append("Individual RapidDeploy deployment logs: ").append(System.getProperty("line.separator"));
                    final List<String> includedJobIds = RapidDeployConnector.extractIncludedJobIdsUnderPipelineJob(jobDetails);
                    for (final String internalJobId : includedJobIds) {
                        logUrls.append("  ").append(baseLogUrl).append(internalJobId).append(System.getProperty("line.separator"));
                    }
                }

                if (!success) {
                    throw new RuntimeException(
                    "RapidDeploy job failed." + System.getProperty("line.separator") + "You can check the RapidDeploy logs here: " + logUrls);
                }
                logger.println("RapidDeploy job successfully run.");
                logger.println("You can check the RapidDeploy logs here: " + logUrls);
            }
        } else {
            throw new RuntimeException("Could not retrieve job ID, possibly running asynchronously!");
        }
        return success;
    }
}