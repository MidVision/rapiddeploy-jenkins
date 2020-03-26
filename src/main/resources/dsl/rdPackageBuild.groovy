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
	final def packageName = args.packageName
	if (!packageName) {
		println "INFO: package name not provided, an automatic version incremental name will be applied"
		packageName = ""
	}
	final def archiveExtension = args.archiveExtension
	if (!archiveExtension) {
		println "INFO: archive extension not provided, defaulting to 'jar'"
		archiveExtension = "jar"
	}

	// Show the parameters
	println "Invoking RapidDeploy project deploy via path..."
	println "  > Server URL:        " + serverUrl
	println "  > Project:           " + project
	println "  > Package name:      " + packageName
	println "  > Archive extension: " + archiveExtension

	// Execute the package creation process
	try {
		final def output = RapidDeployConnector.invokeRapidDeployBuildPackage(authToken.toString(), serverUrl.toString(), project.toString(), packageName.toString(), archiveExtension.toString(),
				false, true)
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
				if ((jobStatus.equals("DEPLOYING")) || (jobStatus.equals("QUEUED")) || (jobStatus.equals("STARTING")) || (jobStatus.equals("EXECUTING"))) {
					println "Job running, next check in 30 seconds..."
					milisToSleep = 30000L
				} else if ((jobStatus.equals("REQUESTED")) || (jobStatus.equals("REQUESTED_SCHEDULED"))) {
					println "Job in a REQUESTED state. Approval may be required in RapidDeploy to continue with the execution, next check in 30 seconds..."
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
		return true
	} catch (Exception e) {
		throw e
	}
}