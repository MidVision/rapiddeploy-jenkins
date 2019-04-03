package com.midvision.rapiddeploy.plugin.jenkins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.midvision.rapiddeploy.connector.RapidDeployConnector;
import com.midvision.rapiddeploy.plugin.jenkins.postbuildstep.RapidDeployPackageBuilder;

import hudson.model.AbstractBuild;
import hudson.model.BuildListener;

public class RapidDeployConnectorProxy {

	private static final Log logger = LogFactory.getLog(RapidDeployPackageBuilder.class);

	private List<String> projects;
	private List<String> jobPlans;
	private boolean newConnection = true;

	public boolean isNewConnection() {
		return newConnection;
	}

	public void setNewConnection(final boolean newConnection) {
		this.newConnection = newConnection;
	}

	/*****************************/
	/** PACKAGE CREATION METHOS **/
	/*****************************/

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
			final String output = RapidDeployConnector.invokeRapidDeployBuildPackage(authenticationToken, serverUrl, project, packageName, archiveExtension,
					false, true);
			boolean success = true;
			final String jobId = RapidDeployConnector.extractJobId(output);
			if (jobId != null) {
				listener.getLogger().println("Checking job status every 30 seconds...");
				boolean runningJob = true;
				long milisToSleep = 30000L;
				while (runningJob) {
					Thread.sleep(milisToSleep);
					final String jobDetails = RapidDeployConnector.pollRapidDeployJobDetails(authenticationToken, serverUrl, jobId);
					final String jobStatus = RapidDeployConnector.extractJobStatus(jobDetails);
					listener.getLogger().println("Job status: " + jobStatus);
					if ((jobStatus.equals("DEPLOYING")) || (jobStatus.equals("QUEUED")) || (jobStatus.equals("STARTING")) || (jobStatus.equals("EXECUTING"))) {
						listener.getLogger().println("Job running, next check in 30 seconds...");
						milisToSleep = 30000L;
					} else if ((jobStatus.equals("REQUESTED")) || (jobStatus.equals("REQUESTED_SCHEDULED"))) {
						listener.getLogger().println("Job in a REQUESTED state. Approval may be required in RapidDeploy "
								+ "to continue with the execution, next check in 30 seconds...");
					} else if (jobStatus.equals("SCHEDULED")) {
						listener.getLogger().println("Job in a SCHEDULED state, the execution will start in a future date, next check in 5 minutes...");
						listener.getLogger().println("Printing out job details: ");
						listener.getLogger().println(jobDetails);
						milisToSleep = 300000L;
					} else {
						runningJob = false;
						listener.getLogger().println("Job finished with status: " + jobStatus);
						if ((jobStatus.equals("FAILED")) || (jobStatus.equals("REJECTED")) || (jobStatus.equals("CANCELLED"))
								|| (jobStatus.equals("UNEXECUTABLE")) || (jobStatus.equals("TIMEDOUT")) || (jobStatus.equals("UNKNOWN"))) {
							success = false;
						}
					}
				}
			} else {
				throw new RuntimeException("Could not retrieve job id, running asynchronously!");
			}
			final String logs = RapidDeployConnector.pollRapidDeployJobLog(authenticationToken, serverUrl, jobId);
			if (!success) {
				throw new RuntimeException("RapidDeploy job failed. Please check the output." + System.getProperty("line.separator") + logs);
			}
			listener.getLogger().println("RapidDeploy job successfully run. Please check the output.");
			listener.getLogger().println();
			listener.getLogger().println(logs);
			return true;
		} catch (final Exception e) {
			listener.getLogger().println("Call failed with error: " + e.getMessage());
			return false;
		}
	}

	/***************************/
	/** JOB DEPLOYMENT METHOS **/
	/***************************/

	public static boolean performJobDeployment(final AbstractBuild<?, ?> build, final BuildListener listener, final String serverUrl,
			final String authenticationToken, final String project, final String target, String packageName, final Boolean asynchronousJob) {
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
		listener.getLogger().println("  > Data dictionary: " + dataDictionary);
		listener.getLogger().println();
		try {
			final String output = RapidDeployConnector.invokeRapidDeployDeploymentPollOutput(authenticationToken, serverUrl, project, target, packageName,
					false, true, dataDictionary);
			if (!asynchronousJob) {
				boolean success = true;
				final String jobId = RapidDeployConnector.extractJobId(output);
				if (jobId != null) {
					listener.getLogger().println("Checking job status every 30 seconds...");
					boolean runningJob = true;
					long milisToSleep = 30000L;
					while (runningJob) {
						Thread.sleep(milisToSleep);
						final String jobDetails = RapidDeployConnector.pollRapidDeployJobDetails(authenticationToken, serverUrl, jobId);
						final String jobStatus = RapidDeployConnector.extractJobStatus(jobDetails);
						listener.getLogger().println("Job status: " + jobStatus);
						if ((jobStatus.equals("DEPLOYING")) || (jobStatus.equals("QUEUED")) || (jobStatus.equals("STARTING"))
								|| (jobStatus.equals("EXECUTING"))) {
							listener.getLogger().println("Job running, next check in 30 seconds...");
							milisToSleep = 30000L;
						} else if ((jobStatus.equals("REQUESTED")) || (jobStatus.equals("REQUESTED_SCHEDULED"))) {
							listener.getLogger().println("Job in a REQUESTED state. Approval may be required in RapidDeploy "
									+ "to continue with the execution, next check in 30 seconds...");
						} else if (jobStatus.equals("SCHEDULED")) {
							listener.getLogger().println("Job in a SCHEDULED state, the execution will start in a future date, next check in 5 minutes...");
							listener.getLogger().println("Printing out job details: ");
							listener.getLogger().println(jobDetails);
							milisToSleep = 300000L;
						} else {
							runningJob = false;
							listener.getLogger().println("Job finished with status: " + jobStatus);
							if ((jobStatus.equals("FAILED")) || (jobStatus.equals("REJECTED")) || (jobStatus.equals("CANCELLED"))
									|| (jobStatus.equals("UNEXECUTABLE")) || (jobStatus.equals("TIMEDOUT")) || (jobStatus.equals("UNKNOWN"))) {
								success = false;
							}
						}
					}
				} else {
					throw new RuntimeException("Could not retrieve job id, running asynchronously!");
				}
				final String logs = RapidDeployConnector.pollRapidDeployJobLog(authenticationToken, serverUrl, jobId);
				if (!success) {
					throw new RuntimeException("RapidDeploy job failed. Please check the output." + System.getProperty("line.separator") + logs);
				}
				listener.getLogger().println("RapidDeploy job successfully run. Please check the output.");
				listener.getLogger().println();
				listener.getLogger().println(logs);
			}
			return true;
		} catch (final Exception e) {
			listener.getLogger().println("Call failed with error: " + e.getMessage());
			return false;
		}
	}

	/*************************/
	/** JOB PLAN RUN METHOS **/
	/*************************/

	public static boolean performJobPlanRun(final BuildListener listener, final String serverUrl, final String authenticationToken, final String jobPlan,
			final Boolean asynchronousJob, final Boolean showFullLogs) {
		listener.getLogger().println("Invoking RapidDeploy project deploy via path...");
		listener.getLogger().println("  > Server URL: " + serverUrl);
		listener.getLogger().println("  > jobPlan: " + jobPlan);
		listener.getLogger().println("  > Asynchronous? " + asynchronousJob);
		listener.getLogger().println("  > Show Full Logs? " + showFullLogs);
		listener.getLogger().println();
		final String jobPlanId = jobPlan.substring(jobPlan.indexOf("[") + 1, jobPlan.indexOf("]"));
		try {
			final String output = RapidDeployConnector.invokeRapidDeployJobPlanPollOutput(authenticationToken, serverUrl, jobPlanId, true);
			if (!asynchronousJob) {
				String jobDetails = "";
				boolean success = true;
				final String jobId = RapidDeployConnector.extractJobId(output);
				if (jobId != null) {
					listener.getLogger().println("Checking job status every 30 seconds...");
					boolean runningJob = true;
					long milisToSleep = 30000L;
					while (runningJob) {
						Thread.sleep(milisToSleep);
						jobDetails = RapidDeployConnector.pollRapidDeployJobDetails(authenticationToken, serverUrl, jobId);
						final String jobStatus = RapidDeployConnector.extractJobStatus(jobDetails);
						listener.getLogger().println("Job status: " + jobStatus);
						if ((jobStatus.equals("DEPLOYING")) || (jobStatus.equals("QUEUED")) || (jobStatus.equals("STARTING"))
								|| (jobStatus.equals("EXECUTING"))) {
							listener.getLogger().println("Job running, next check in 30 seconds...");
							milisToSleep = 30000L;
						} else if ((jobStatus.equals("REQUESTED")) || (jobStatus.equals("REQUESTED_SCHEDULED"))) {
							listener.getLogger().println("Job in a REQUESTED state. Approval may be required in RapidDeploy "
									+ "to continue with the execution, next check in 30 seconds...");
						} else if (jobStatus.equals("SCHEDULED")) {
							listener.getLogger().println("Job in a SCHEDULED state, the execution will start in a future date, next check in 5 minutes...");
							listener.getLogger().println("Printing out job details: ");
							listener.getLogger().println(jobDetails);
							milisToSleep = 300000L;
						} else {
							runningJob = false;
							listener.getLogger().println("Job finished with status: " + jobStatus);
							if ((jobStatus.equals("FAILED")) || (jobStatus.equals("REJECTED")) || (jobStatus.equals("CANCELLED"))
									|| (jobStatus.equals("UNEXECUTABLE")) || (jobStatus.equals("TIMEDOUT")) || (jobStatus.equals("UNKNOWN"))) {
								success = false;
							}
						}
					}
				} else {
					throw new RuntimeException("Could not retrieve job id, running asynchronously!");
				}
				final String logs = RapidDeployConnector.pollRapidDeployJobLog(authenticationToken, serverUrl, jobId);
				final StringBuilder fullLogs = new StringBuilder();
				fullLogs.append(logs).append("\n");

				if (showFullLogs) {
					final List<String> includedJobIds = RapidDeployConnector.extractIncludedJobIdsUnderPipelineJob(jobDetails);
					for (final String internalJobId : includedJobIds) {
						fullLogs.append("LOGS RELATED TO JOB ID: ").append(internalJobId).append("\n");
						fullLogs.append(RapidDeployConnector.pollRapidDeployJobLog(authenticationToken, serverUrl, internalJobId));
					}
				}
				if (!success) {
					throw new RuntimeException("RapidDeploy job failed. Please check the output." + System.getProperty("line.separator") + logs);
				}
				listener.getLogger().println("RapidDeploy job successfully run. Please check the output.");
				listener.getLogger().println();
				listener.getLogger().println(fullLogs.toString());
			}
			return true;
		} catch (final Exception e) {
			listener.getLogger().println("Call failed with error: " + e.getMessage());
			return false;
		}
	}

	/**********************/
	/***** AUX METHOS *****/
	/**********************/

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

	/************************/
	/***** PROXY METHOS *****/
	/************************/

	/** Method that caches the projects to ease the form validation **/
	public List<String> getProjects(final String serverUrl, final String authenticationToken) {
		logger.debug("getProjects");
		if (projects == null || projects.isEmpty() || newConnection) {
			try {
				if (serverUrl != null && !"".equals(serverUrl) && authenticationToken != null && !"".equals(authenticationToken)) {
					logger.debug("REQUEST TO WEB SERVICE GET PROJECTS...");
					projects = RapidDeployConnector.invokeRapidDeployListProjects(authenticationToken, serverUrl);
					newConnection = false;
					logger.debug("PROJECTS RETRIEVED: " + projects.size());
				} else {
					projects = new ArrayList<String>();
				}
			} catch (final Exception e) {
				logger.warn(e.getMessage());
				projects = new ArrayList<String>();
			}
		}
		logger.debug("PROJECTS: " + projects.size());
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
			logger.error("Invalid target settings found! Target: " + target);
		}
		return packageNames;
	}

	public List<String> getJobPlans(final String serverUrl, final String authenticationToken) {
		logger.debug("getJobPlans");
		if (jobPlans == null || jobPlans.isEmpty() || newConnection) {
			try {
				jobPlans = new ArrayList<String>();
				if (serverUrl != null && !"".equals(serverUrl) && authenticationToken != null && !"".equals(authenticationToken)) {
					logger.debug("REQUEST TO WEB SERVICE GET JOB PLANS...");
					final String jobPlansCallOutput = RapidDeployConnector.invokeRapidDeployJobPlans(authenticationToken, serverUrl);
					final Map<String, String> jobPlansExtracted = RapidDeployConnector.extractJobPlansFromXml(jobPlansCallOutput);
					for (final String jobPlanDesc : jobPlansExtracted.values()) {
						jobPlans.add(jobPlanDesc);
					}
					newConnection = false;
					logger.debug("JOB PLANS RETRIEVED: " + jobPlans.size());
				} else {
					jobPlans = new ArrayList<String>();
				}
			} catch (final Exception e) {
				logger.warn(e.getMessage());
				jobPlans = new ArrayList<String>();
			}
		}
		logger.debug("JOB PLANS: " + jobPlans.size());
		return jobPlans;
	}

	public String createPackagesTable(final String serverUrl, final String authenticationToken, final String project) {
		List<String> packageNames = new ArrayList<String>();
		try {
			packageNames = RapidDeployConnector.invokeRapidDeployListPackages(authenticationToken, serverUrl, project);
		} catch (final Exception e) {
			logger.warn(e.getMessage());
		}
		if (!packageNames.isEmpty()) {
			final StringBuffer sb = new StringBuffer();
			sb.append("<table>");
			int index = 0;
			final int limit = 10;
			for (final String packageName : packageNames) {
				if (!"null".equals(packageName) && !packageName.startsWith("Deployment")) {
					sb.append("<tr><td class=\"setting-main\">");
					sb.append(packageName);
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
