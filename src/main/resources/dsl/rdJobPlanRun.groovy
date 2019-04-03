package dsl

import com.midvision.rapiddeploy.connector.RapidDeployConnector

def call(args = [:]) {

	// Load and check all the parameters
	final def serverUrl = args.serverUrl
	if (!serverUrl) {
		println "WARN: RapidDeploy URL not provided, defaulting to 'http://localhost:9090/MidVision'"
		serverUrl = "http://localhost:9090/MidVision"
	} else if (!serverUrl.startsWith("http")) {
		throw new Exception("Missing RapidDeploy URL protocol: 'http' or 'https'")
	}
	final def authToken = args.authToken
	if (!authToken) {
		println "WARN: authentication token not provided!"
		authToken = ""
	}
	final def jobPlanId = args.jobPlanId
	if (jobPlanId == null) {
		throw new Exception("Missing the RapidDeploy job plan ID to deploy!")
	}
	final def asynchronous = args.asynchronous
	if (asynchronous == null) {
		println "INFO: 'asynchronous' parameter not provided, running job synchronously"
		asynchronous = false
	}
	final def showFullLogs = args.showFullLogs
	if (showFullLogs == null) {
		println "INFO: 'showFullLogs' parameter not provided, defaulting to 'false'"
		showFullLogs = false
	}

	// Show the parameters
	println "Invoking RapidDeploy project deploy via path..."
	println "  > Server URL:     " + serverUrl
	println "  > Job plan ID:    " + jobPlanId
	println "  > Asynchronous?   " + asynchronous
	println "  > Show full logs? " + showFullLogs

	// Execute the job plan run process
	try {
		final def output = RapidDeployConnector.invokeRapidDeployJobPlanPollOutput(authToken.toString(), serverUrl.toString(), jobPlanId.toString(), true)
		if (!asynchronous) {
			def jobDetails = ""
			boolean success = true
			final def jobId = RapidDeployConnector.extractJobId(output)
			if (jobId != null) {
				println "Checking job status every 30 seconds..."
				boolean runningJob = true
				long milisToSleep = 30000L
				while (runningJob) {
					Thread.sleep(milisToSleep)
					jobDetails = RapidDeployConnector.pollRapidDeployJobDetails(authToken.toString(), serverUrl.toString(), jobId)
					final def jobStatus = RapidDeployConnector.extractJobStatus(jobDetails)
					println "Job status: " + jobStatus
					if ((jobStatus.equals("DEPLOYING")) || (jobStatus.equals("QUEUED")) || (jobStatus.equals("STARTING"))
					|| (jobStatus.equals("EXECUTING"))) {
						println "Job running, next check in 30 seconds..."
						milisToSleep = 30000L
					} else if ((jobStatus.equals("REQUESTED")) || (jobStatus.equals("REQUESTED_SCHEDULED"))) {
						println "Job in a REQUESTED state. Approval may be required in RapidDeploy "
						+ "to continue with the execution, next check in 30 seconds..."
					} else if (jobStatus.equals("SCHEDULED")) {
						println "Job in a SCHEDULED state, the execution will start in a future date, next check in 5 minutes..."
						println "Printing out job details: "
						println jobDetails
						milisToSleep = 300000L
					} else {
						runningJob = false
						println "Job finished with status: " + jobStatus
						if ((jobStatus.equals("FAILED")) || (jobStatus.equals("REJECTED")) || (jobStatus.equals("CANCELLED"))
						|| (jobStatus.equals("UNEXECUTABLE")) || (jobStatus.equals("TIMEDOUT")) || (jobStatus.equals("UNKNOWN"))) {
							success = false
						}
					}
				}
			} else {
				throw new Exception("Could not retrieve job id, running asynchronously!")
			}

			def logs = RapidDeployConnector.pollRapidDeployJobLog(authToken.toString(), serverUrl.toString(), jobId)

			if (showFullLogs) {
				final List<String> includedJobIds = RapidDeployConnector.extractIncludedJobIdsUnderPipelineJob(jobDetails)
				for (final String internalJobId : includedJobIds) {
					logs = "${logs}LOGS RELATED TO JOB ID: ${internalJobId}\n"
					logs = "${logs}${RapidDeployConnector.pollRapidDeployJobLog(authToken.toString(), serverUrl.toString(), internalJobId)}"
				}
			}
			if (!success) {
				throw new Exception("RapidDeploy job failed. Please check the output. \n" + logs.toString())
			}
			println "RapidDeploy job successfully run. Please check the output."
			println logs.toString()
		}
		return true
	} catch (Exception e) {
		throw e
	}
}