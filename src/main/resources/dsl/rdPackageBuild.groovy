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
    def packageName = args.packageName
    if (!packageName) {
        println("INFO: package name not provided, an automatic version incremental name will be applied.")
        packageName = ""
    }
    def archiveExtension = args.archiveExtension
    if (!archiveExtension) {
        println("INFO: archive extension not provided, defaulting to 'jar'.")
        archiveExtension = "jar"
    }

    // Show the parameters
    println("Invoking RapidDeploy deployment package creation...")
    println("  > Server URL:        " + serverUrl)
    println("  > Project:           " + project)
    println("  > Package name:      " + packageName)
    println("  > Archive extension: " + archiveExtension)

    // Execute the package creation process
    try {
        final def jobRequestOutput = RapidDeployConnector.invokeRapidDeployBuildPackage(authToken.toString(), serverUrl.toString(), project.toString(), packageName.toString(), archiveExtension.toString(),
                false, true)
        checkJobStatus(new PipelineLogger(this), serverUrl, authToken, jobRequestOutput, false, true)
    } catch (Exception e) {
        throw e
    }
}