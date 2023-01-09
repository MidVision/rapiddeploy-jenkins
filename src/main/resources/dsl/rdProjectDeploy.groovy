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
    final def project = args.project
    if (!project) {
        throw new Exception("Missing the RapidDeploy project name to deploy!")
    }
    final def target = args.target
    if (!target) {
        throw new Exception("Missing the RapidDeploy target name to deploy to!")
    }
    def deploymentPackageName = args.deploymentPackageName
    if (!deploymentPackageName) {
        println("INFO: deployment package name not provided, defaulting to 'LATEST'.")
        deploymentPackageName = "LATEST"
    }
    def asynchronous = args.asynchronous
    if (asynchronous == null) {
        println("INFO: 'asynchronous' parameter not provided, running job synchronously.")
        asynchronous = false
    }
    def showFullLog = args.showFullLog
    if (showFullLog == null) {
        println("INFO: 'showFullLog' parameter not provided, showing full log by default.")
        showFullLog = true
    }
    def dictionary = args.dictionary ?: [:]
    if (!(dictionary instanceof Map)) {
        println("WARN: wrong value provided for the 'dictionary' parameter, it needs to be a string map, defaulting to an empty map.")
        dictionary = [:]
    }
    dictionary = dictionary.collectEntries { key, value ->
        [ (key) : value.toString() ]
    }

    // Show the parameters
    println("Invoking RapidDeploy project deploy...")
    println("  > Server URL:         " + serverUrl)
    println("  > Project:            " + project)
    println("  > Target:             " + target)
    println("  > Deployment package: " + deploymentPackageName)
    println("  > Asynchronous?       " + asynchronous)
    println("  > Show full log?      " + showFullLog)
    println("  > Data dictionary:    " + dictionary)

    // Execute the deployment process
    try {
        final def jobRequestOutput = RapidDeployConnector.invokeRapidDeployDeploymentPollOutput(authToken.toString(), serverUrl.toString(),
                project.toString(), target.toString(), deploymentPackageName.toString(), false, true, dictionary)
        if (!asynchronous) {
            checkJobStatus(new PipelineLogger(this), serverUrl, authToken, jobRequestOutput, false, showFullLog)
        }
    } catch (Exception e) {
        throw e
    }
}