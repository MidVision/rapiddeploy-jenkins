package com.midvision.rapiddeploy.plugin.jenkins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.midvision.rapiddeploy.connector.RapidDeployConnector;
import com.midvision.rapiddeploy.plugin.jenkins.log.JobLogger;
import com.midvision.rapiddeploy.plugin.jenkins.log.Logger;

import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;

public class RapidDeployConnectorProxy {

    private static final Log LOG = LogFactory.getLog(RapidDeployConnectorProxy.class);

    /** RapidDeploy deployment statuses **/
    public static final String UNKNOWN = "UNKNOWN";
    public static final String REJECTED = "REJECTED";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String STARTING = "STARTING";
    public static final String EXECUTING = "EXECUTING";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";
    public static final String FAILED = "FAILED";
    public static final String UNEXECUTABLE = "UNEXECUTABLE";
    public static final String TIMED_OUT = "TIMED_OUT";
    public static final String REQUESTED = "REQUESTED";
    public static final String SCHEDULED = "SCHEDULED";
    public static final String BATCHED = "BATCHED";
    public static final String JOB_HALTED = "JOB_HALTED";
    public static final String TASK_HALTED = "TASK_HALTED";
    public static final String REQUESTED_EXECUTING = "REQUESTED_EXECUTING";
    public static final String REQUESTED_SCHEDULED = "REQUESTED_SCHEDULED";
    public static final String RESUMING = "RESUMING";

    /** Messages **/
    public static final String NOT_EMPTY_MESSAGE = "Please set a value for this field!";
    public static final String NO_PROTOCOL_MESSAGE = "Please specify a protocol for the URL, e.g. \"http://\".";
    public static final String CONNECTION_BAD_MESSAGE = "Unable to establish connection.";
    public static final String WRONG_PROJECT_MESSAGE = "Wrong project selected, please reload the projects list.";
    public static final String INSUFFICIENT_PERMISSIONS_MESSAGE = "Insufficient permissions to perform the check.";

    private List<String> projects;
    private List<String> jobPlans;
    private boolean newConnection = true;

    public boolean isNewConnection() {
        return newConnection;
    }

    public void setNewConnection(final boolean newConnection) {
        this.newConnection = newConnection;
    }

    /******************************/
    /** PACKAGE CREATION METHODS **/
    /******************************/

    public static boolean performPackageBuild(final AbstractBuild<?, ?> build, final BuildListener listener, final String serverUrl,
            final String authenticationToken, final String project, String packageName, final String archiveExtension) {
        if (StringUtils.isNotBlank(packageName)) {
            packageName = replaceParametersPlaceholders(packageName, build, listener);
        }

        listener.getLogger().println("Invoking RapidDeploy deployment package builder...");
        listener.getLogger().println("  > Server URL: " + serverUrl);
        listener.getLogger().println("  > Project: " + project);
        listener.getLogger().println("  > Package name: " + packageName);
        listener.getLogger().println("  > Archive extension: " + archiveExtension);
        listener.getLogger().println();
        try {
            final String jobRequestOutput = RapidDeployConnector.invokeRapidDeployBuildPackage(authenticationToken, serverUrl, project, packageName,
                    archiveExtension, false, true);
            return checkJobStatus(new JobLogger(listener.getLogger()), serverUrl, authenticationToken, jobRequestOutput, false, true);
        } catch (final Exception e) {
            listener.getLogger().println("Call failed with error: " + e.getMessage());
            return false;
        }
    }

    /****************************/
    /** JOB DEPLOYMENT METHODS **/
    /****************************/

    public static boolean performJobDeployment(final AbstractBuild<?, ?> build, final BuildListener listener, final String serverUrl,
            final String authenticationToken, final String project, final String target, String packageName, final Boolean asynchronousJob,
            final Boolean showFullLog) {
        if (StringUtils.isNotBlank(packageName)) {
            packageName = replaceParametersPlaceholders(packageName, build, listener);
        }

        listener.getLogger().println("Retrieving the list of data dictionary items...");
        final Map<String, String> dataDictionary = new HashMap<String, String>();
        try {
            for (final Entry<String, String> envVar : build.getEnvironment(listener).entrySet()) {
                final Pattern pattern = Pattern.compile("@@.+@@");
                final Matcher matcher = pattern.matcher(envVar.getKey());
                if (matcher.matches()) {
                    dataDictionary.put(envVar.getKey(), envVar.getValue());
                }
            }
        } catch (final IOException e1) {
            listener.getLogger().println("WARNING: Unable to retrieve the list of parameters. No data dictionary passed to the deployment.");
        } catch (final InterruptedException e1) {
            listener.getLogger().println("WARNING: Unable to retrieve the list of parameters. No data dictionary passed to the deployment.");
        }

        listener.getLogger().println("Invoking RapidDeploy project deploy via path...");
        listener.getLogger().println("  > Server URL: " + serverUrl);
        listener.getLogger().println("  > Project: " + project);
        listener.getLogger().println("  > Target: " + target);
        listener.getLogger().println("  > Package: " + packageName);
        listener.getLogger().println("  > Asynchronous? " + asynchronousJob);
        listener.getLogger().println("  > Show full log? " + showFullLog);
        listener.getLogger().println("  > Data dictionary: " + dataDictionary);
        listener.getLogger().println();
        try {
            final String jobRequestOutput = RapidDeployConnector.invokeRapidDeployDeploymentPollOutput(authenticationToken, serverUrl, project, target,
                    packageName, false, true, dataDictionary);
            if (!asynchronousJob) {
                return checkJobStatus(new JobLogger(listener.getLogger()), serverUrl, authenticationToken, jobRequestOutput, false, showFullLog);
            }
            return true;
        } catch (final Exception e) {
            listener.getLogger().println("Call failed with error: " + e.getMessage());
            return false;
        }
    }

    /**************************/
    /** JOB PLAN RUN METHODS **/
    /**************************/

    public static boolean performJobPlanRun(final BuildListener listener, final String serverUrl, final String authenticationToken, final String jobPlan,
            final Boolean asynchronousJob, final Boolean showIndividualLogs, final Boolean showFullLog) {
        listener.getLogger().println("Invoking RapidDeploy job plan execution via path...");
        listener.getLogger().println("  > Server URL: " + serverUrl);
        listener.getLogger().println("  > jobPlan: " + jobPlan);
        listener.getLogger().println("  > Asynchronous? " + asynchronousJob);
        listener.getLogger().println("  > Show individual logs? " + showIndividualLogs);
        listener.getLogger().println("  > Show full log? " + showFullLog);
        listener.getLogger().println();
        final String jobPlanId = jobPlan.substring(jobPlan.indexOf("[") + 1, jobPlan.indexOf("]"));
        try {
            final String jobRequestOutput = RapidDeployConnector.invokeRapidDeployJobPlanPollOutput(authenticationToken, serverUrl, jobPlanId, true);
            if (!asynchronousJob) {
                return checkJobStatus(new JobLogger(listener.getLogger()), serverUrl, authenticationToken, jobRequestOutput, showIndividualLogs, showFullLog);
            }
            return true;
        } catch (final Exception e) {
            listener.getLogger().println("Call failed with error: " + e.getMessage());
            return false;
        }
    }

    /***********************/
    /***** AUX METHODS *****/
    /***********************/

    private static boolean checkJobStatus(final Logger logger, final String serverUrl, final String authenticationToken, final String jobRequestOutput,
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
                if (jobStatus.equals(SUBMITTED) || jobStatus.equals(STARTING) || jobStatus.equals(EXECUTING) || jobStatus.equals(BATCHED)
                        || jobStatus.equals(RESUMING)) {
                    logger.println("Job running, next check in 30 seconds...");
                    milisToSleep = 30000L;
                } else if (jobStatus.equals(REQUESTED) || jobStatus.equals(REQUESTED_SCHEDULED) || jobStatus.equals(REQUESTED_EXECUTING)) {
                    logger.println("Job in a REQUESTED state. Approval may be required in RapidDeploy "
                            + "to continue with the execution, next check in 30 seconds...");
                } else if (jobStatus.equals(JOB_HALTED) || jobStatus.equals(TASK_HALTED)) {
                    logger.println("Job in a HALTED state, next check in 5 minutes...");
                    logger.println("Printing out job details: ");
                    logger.println(jobDetails);
                    milisToSleep = 300000L;
                } else if (jobStatus.equals(SCHEDULED)) {
                    logger.println("Job in a SCHEDULED state, the execution will start in a future date, next check in 5 minutes...");
                    logger.println("Printing out job details: ");
                    logger.println(jobDetails);
                    milisToSleep = 300000L;
                } else {
                    runningJob = false;
                    logger.println("Job finished with status: " + jobStatus);
                    if (jobStatus.equals(COMPLETED)) {
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

    private static String replaceParametersPlaceholders(String paramStr, final AbstractBuild<?, ?> build, final BuildListener listener) {
        listener.getLogger().println("Replacing job parameters for '" + paramStr + "'");

        // First we need to retrieve all the placeholders: '${xxx}'
        final Pattern pattern = Pattern.compile("\\$\\{[^\\$\\{\\}]+\\}");
        // Then we need to extract the string inside the placeholder
        final Pattern inPattern = Pattern.compile("\\$\\{(.+)\\}");

        String group;
        String replaceStr;
        final Matcher matcher = pattern.matcher(paramStr);
        Matcher inMatcher;

        // We iterate over the placeholders found
        while (matcher.find()) {
            group = matcher.group();
            listener.getLogger().println("Job parameter found: " + group);
            inMatcher = inPattern.matcher(group);
            // Obtain the string inside the placeholder
            if (inMatcher.matches()) {
                try {
                    // Get the value of the parameter
                    replaceStr = build.getEnvironment(listener).get(inMatcher.group(1));
                    listener.getLogger().println("Job parameter value retrieved: " + replaceStr);
                    // If the value is not blank, replace the parameter
                    if (StringUtils.isNotBlank(replaceStr)) {
                        listener.getLogger().println("Retrieved value '" + replaceStr + "' from job parameter '" + group + "'");
                        paramStr = paramStr.replace(group, replaceStr);
                    } else {
                        listener.getLogger().println("WARNING: job parameter not found '" + group + "'");
                    }
                } catch (final Exception e) {
                    listener.getLogger().println("WARNING: Unable to retrieve the job parameter '" + group + "'");
                    listener.getLogger().println("         " + e.getMessage());
                }
            }
        }
        listener.getLogger().println("Replaced value '" + paramStr + "'");
        return paramStr;
    }

    private Map<String, String> sortByJobPlanName(final Map<String, String> unsortMap) {

        // 1. Convert Map to List of Map
        final List<Entry<String, String>> list = new LinkedList<Map.Entry<String, String>>(unsortMap.entrySet());

        // 2. Sort list with Collections.sort(), provide a custom Comparator
        // Try switch the o1 o2 position for a different order
        Collections.sort(list, new Comparator<Map.Entry<String, String>>() {
            public int compare(final Map.Entry<String, String> o1, final Map.Entry<String, String> o2) {
                return (o1.getValue().substring(o1.getValue().indexOf("]") + 2)).compareTo(o2.getValue().substring(o2.getValue().indexOf("]") + 2));
            }
        });

        // 3. Loop the sorted list and put it into a new insertion order Map LinkedHashMap
        final Map<String, String> sortedMap = new LinkedHashMap<String, String>();
        for (final Map.Entry<String, String> entry : list) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }

        return sortedMap;
    }

    /*************************/
    /***** PROXY METHODS *****/
    /*************************/

    /** Method that caches the projects to ease the form validation **/
    public List<String> getProjects(final String serverUrl, final String authenticationToken) {
        LOG.debug("getProjects");
        if (projects == null || projects.isEmpty() || newConnection) {
            try {
                if (serverUrl != null && !"".equals(serverUrl) && authenticationToken != null && !"".equals(authenticationToken)) {
                    LOG.debug("REQUEST TO WEB SERVICE GET PROJECTS...");
                    projects = RapidDeployConnector.invokeRapidDeployListProjects(authenticationToken, serverUrl);
                    newConnection = false;
                    LOG.debug("PROJECTS RETRIEVED: " + projects.size());
                } else {
                    projects = new ArrayList<String>();
                }
            } catch (final Exception e) {
                LOG.warn(e.getMessage());
                projects = new ArrayList<String>();
            }
        }
        LOG.debug("PROJECTS: " + projects.size());
        return projects;
    }

    public List<String> getTargets(final String serverUrl, final String authenticationToken, final String project) throws Exception {
        return RapidDeployConnector.invokeRapidDeployListTargets(authenticationToken, serverUrl, project);
    }

    public List<String> getDeploymentPackages(final String serverUrl, final String authenticationToken, final String project, final String target)
            throws Exception {
        final String[] targetObjects = target.split("\\.");
        List<String> packageNames = new ArrayList<String>();
        if (target.contains(".") && targetObjects.length == 4) {
            packageNames = RapidDeployConnector.invokeRapidDeployListPackages(authenticationToken, serverUrl, project, targetObjects[0], targetObjects[1],
                    targetObjects[2]);
        } else if (target.contains(".") && targetObjects.length == 3) {
            // support for RD v3.5+ - instance removed
            packageNames = RapidDeployConnector.invokeRapidDeployListPackages(authenticationToken, serverUrl, project, targetObjects[0], targetObjects[1],
                    null);
        } else {
            LOG.error("Invalid target settings found! Target: " + target);
        }
        return packageNames;
    }

    public List<String> getJobPlans(final String serverUrl, final String authenticationToken) {
        LOG.debug("getJobPlans");
        if (jobPlans == null || jobPlans.isEmpty() || newConnection) {
            try {
                jobPlans = new ArrayList<String>();
                if (serverUrl != null && !"".equals(serverUrl) && authenticationToken != null && !"".equals(authenticationToken)) {
                    LOG.debug("REQUEST TO WEB SERVICE GET JOB PLANS...");
                    final String jobPlansCallOutput = RapidDeployConnector.invokeRapidDeployJobPlans(authenticationToken, serverUrl);
                    Map<String, String> jobPlansExtracted = RapidDeployConnector.extractJobPlansFromXml(jobPlansCallOutput);
                    jobPlansExtracted = sortByJobPlanName(jobPlansExtracted);
                    for (final String jobPlanDesc : jobPlansExtracted.values()) {
                        jobPlans.add(jobPlanDesc);
                    }
                    newConnection = false;
                    LOG.debug("JOB PLANS RETRIEVED: " + jobPlans.size());
                } else {
                    jobPlans = new ArrayList<String>();
                }
            } catch (final Exception e) {
                LOG.warn(e.getMessage());
                jobPlans = new ArrayList<String>();
            }
        }
        LOG.debug("JOB PLANS: " + jobPlans.size());
        return jobPlans;
    }

    public String createPackagesTable(final String serverUrl, final String authenticationToken, final String project) {
        List<String> packageNames = new ArrayList<String>();
        try {
            packageNames = RapidDeployConnector.invokeRapidDeployListPackages(authenticationToken, serverUrl, project);
        } catch (final Exception e) {
            LOG.warn(e.getMessage());
        }
        if (!packageNames.isEmpty()) {
            final StringBuffer sb = new StringBuffer();
            sb.append("<table>");
            int index = 0;
            final int limit = 10;
            for (final String packageName : packageNames) {
                if (!"null".equals(packageName) && !packageName.startsWith("Deployment")) {
                    sb.append("<tr><td class=\"setting-main\">");
                    sb.append(Util.escape(packageName));
                    sb.append("</td></tr>");
                    index++;
                    if (index >= limit) {
                        break;
                    }
                }
            }
            sb.append("</table>");
            return sb.toString();
        }
        return null;
    }
}
