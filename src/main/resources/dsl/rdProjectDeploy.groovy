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
	final def project = args.project
	if (!project) {
		throw new Exception("Missing the RapidDeploy project name to deploy!")
	}
	final def target = args.target
	if (!target) {
		throw new Exception("Missing the RapidDeploy target name to deploy to!")
	}
	final def deploymentPackageName = args.deploymentPackageName
	if (!deploymentPackageName) {
		println "INFO: deployment package name not provided, defaulting to 'LATEST'"
		deploymentPackageName = "LATEST"
	}
	final def asynchronous = args.asynchronous
	if (asynchronous == null) {
		println "INFO: 'asynchronous' parameter not provided, running job synchronously"
		asynchronous = false
	}
	final def dictionary = args.dictionary ?: [:]
	if (!(dictionary instanceof Map)) {
		println "WARN: wrong value provided for the 'dictionary' parameter, it needs to be a string map, defaulting to an empty map."
		dictionary = [:]
	}
	dictionary = dictionary.collectEntries { key, value ->
		[ (key) : value.toString() ]
	}

	// Show the parameters
	println "Invoking RapidDeploy project deploy via path..."
	println "  > Server URL:         " + serverUrl
	println "  > Project:            " + project
	println "  > Target:             " + target
	println "  > Deployment package: " + deploymentPackageName
	println "  > Asynchronous?       " + asynchronous
	println "  > Data dictionary:    " + dictionary

	// Execute the deployment process
	try {
		final def output = RapidDeployConnector.invokeRapidDeployDeploymentPollOutput(authToken.toString(), serverUrl.toString(),
				project.toString(), target.toString(), deploymentPackageName.toString(), false, true, dictionary)
		if (!asynchronous) {
			boolean success = true
			final def jobId = RapidDeployConnector.extractJobId(output)
			if (jobId != null) {
				println "Checking job status every 30 seconds..."
				boolean runningJob = true
				long milisToSleep = 30000L
				while (runningJob) {
					Thread.sleep(milisToSleep)
					final def jobDetails = RapidDeployConnector.pollRapidDeployJobDetails(authToken.toString(), serverUrl.toString(), jobId)
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
			final def logs = RapidDeployConnector.pollRapidDeployJobLog(authToken.toString(), serverUrl.toString(), jobId)
			if (!success) {
				throw new Exception("RapidDeploy job failed. Please check the output. \n" + logs)
			}
			println "RapidDeploy job successfully run. Please check the output."
			println logs
		}
		return true
	} catch (Exception e) {
		throw e
	}
}