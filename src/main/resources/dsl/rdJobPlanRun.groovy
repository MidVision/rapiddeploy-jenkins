package dsl

import static dsl.RdConnectorProxy.checkJobStatus

import com.midvision.rapiddeploy.connector.RapidDeployConnector
import com.midvision.rapiddeploy.plugin.jenkins.log.PipelineLogger

def call(args = [:]) {

    // Load and check all the parameters
    def serverUrl = args.serverUrl
    if (!serverUrl) {
        println("WARN: RapidDeploy URL not provided! Defaulting to 'http://localhost:9090/MidVision'.")
        serverUrl = "http://localhost:9090/MidVision"
    } else if (!serverUrl.startsWith("http")) {
        throw new Exception("Missing RapidDeploy URL protocol: 'http' or 'https'")
    }
    def authToken = args.authToken
    if (!authToken) {
        println("WARN: authentication token not provided! Defaulting to blank.")
        authToken = ""
    }
    final def jobPlanId = args.jobPlanId
    if (jobPlanId == null) {
        throw new Exception("Missing the RapidDeploy job plan ID to deploy!")
    }
    def asynchronous = args.asynchronous
    if (asynchronous == null) {
        println("INFO: 'asynchronous' parameter not provided, running job synchronously.")
        asynchronous = false
    }
    def showIndividualLogs = args.showIndividualLogs
    if (showIndividualLogs == null) {
        println("INFO: 'showIndividualLogs' parameter not provided, hidding individual logs.")
        showIndividualLogs = false
    }
    def showFullLog = args.showFullLog
    if (showFullLog == null) {
        println("INFO: 'showFullLog' parameter not provided, showing full log by default.")
        showFullLog = true
    }

    // Show the parameters
    println("Invoking RapidDeploy job plan execution...")
    println("  > Server URL:           " + serverUrl)
    println("  > Job plan ID:          " + jobPlanId)
    println("  > Asynchronous?         " + asynchronous)
    println("  > Show individual logs? " + showIndividualLogs)
    println("  > Show full log?        " + showFullLog)

    // Execute the job plan run process
    try {
        final def jobRequestOutput = RapidDeployConnector.invokeRapidDeployJobPlanPollOutput(authToken.toString(), serverUrl.toString(), jobPlanId.toString(), true)
        if (!asynchronous) {
            checkJobStatus(new PipelineLogger(this), serverUrl, authToken, jobRequestOutput, showIndividualLogs, showFullLog)
        }
    } catch (Exception e) {
        throw e
    }
}