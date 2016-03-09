package com.midvision.rapiddeploy.plugin.jenkins.postbuildstep;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.midvision.rapiddeploy.connector.RapidDeployConnector;

@SuppressWarnings("unchecked")
public class RapidDeployJobRunner extends Notifier {

	private final String serverUrl;
	private final String authenticationToken;
	private final String project;
	private final String environment;
	private final String packageName;
	private final Boolean asynchronousJob;

	private static final Log logger = LogFactory.getLog(RapidDeployJobRunner.class);

	@DataBoundConstructor
	public RapidDeployJobRunner(String serverUrl, String authenticationToken, String project, String environment, String packageName, Boolean asynchronousJob) {
		super();
		this.serverUrl = serverUrl;
		this.authenticationToken = authenticationToken;
		this.environment = environment;
		this.packageName = packageName;
		this.project = project;
		this.asynchronousJob = asynchronousJob;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
		listener.getLogger().println("Invoking RapidDeploy project deploy via path...");
		listener.getLogger().println("  > Server URL: " + serverUrl);
		listener.getLogger().println("  > Project: " + project);
		listener.getLogger().println("  > Environment: " + environment);
		listener.getLogger().println("  > Package: " + packageName);
		listener.getLogger().println("  > Asynchronous? " + asynchronousJob);
		try {
			String output = RapidDeployConnector.invokeRapidDeployDeploymentPollOutput(authenticationToken, serverUrl, project, environment, packageName, true,
					asynchronousJob);
			listener.getLogger().println(output);
			if (asynchronousJob) {
				listener.getLogger().println(
						"Job running asynchronously. You can check the results of the deployments here once finished: " + serverUrl + "/ws/feed/" + project
								+ "/list/jobs");
			}
			return true;
		} catch (Exception e) {
			listener.getLogger().println("Call failed with error: " + e.getMessage());
			return false;
		}
	}

	public String getProject() {
		return project;
	}

	public String getServerUrl() {
		return serverUrl;
	}

	public String getAuthenticationToken() {
		return authenticationToken;
	}

	public String getEnvironment() {
		return environment;
	}

	public String getPackageName() {
		return packageName;
	}

	public Boolean getAsynchronousJob() {
		return asynchronousJob;
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	/**
	 * Descriptor for {@link RapidDeployJobRunner}. Used as a singleton. The
	 * class is marked as public so that it can be accessed from views.
	 */
	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

		final private static String NOT_EMPTY_MESSAGE = "Please set a value for this field!";
		final private static String NO_PROTOCOL_MESSAGE = "Please specify a protocol for the URL, e.g. \"http://\".";
		final private static String CONNECTION_BAD_MESSAGE = "Unable to establish connection.";

		private List<String> projects;
		private boolean newConnection = true;

		public DescriptorImpl() {
			super(RapidDeployJobRunner.class);
			load();
		}

		@SuppressWarnings("rawtypes")
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			// Indicates that this builder can be used with all kinds of project
			// types
			return true;
		}

		/**
		 * This human readable name is used in the configuration screen.
		 */
		@Override
		public String getDisplayName() {
			return "RapidDeploy project deploy";
		}

		/** SERVER URL FIELD **/

		public FormValidation doCheckServerUrl(@QueryParameter String value) throws IOException, ServletException {
			logger.debug("doCheckServerUrl");
			newConnection = true;
			if (value.length() == 0) {
				return FormValidation.error(NOT_EMPTY_MESSAGE);
			} else if (!value.startsWith("http://") && !value.startsWith("https://")) {
				return FormValidation.warning(NO_PROTOCOL_MESSAGE);
			}
			return FormValidation.ok();
		}

		/** AUTHENTICATION TOKEN FIELD **/

		public FormValidation doCheckAuthenticationToken(@QueryParameter String value) throws IOException, ServletException {
			logger.debug("doCheckAuthenticationToken");
			newConnection = true;
			if (value.length() == 0) {
				return FormValidation.error(NOT_EMPTY_MESSAGE);
			}
			return FormValidation.ok();
		}

		/** LOAD PROJECTS BUTTON **/

		public FormValidation doLoadProjects(@QueryParameter("serverUrl") final String serverUrl,
				@QueryParameter("authenticationToken") final String authenticationToken) throws IOException, ServletException {
			logger.debug("doLoadProjects");
			newConnection = true;
			if (getProjects(serverUrl, authenticationToken).isEmpty()) {
				return FormValidation.error(CONNECTION_BAD_MESSAGE);
			}
			return FormValidation.ok();
		}

		/** PROJECT FIELD **/

		public ListBoxModel doFillProjectItems(@QueryParameter("serverUrl") final String serverUrl,
				@QueryParameter("authenticationToken") final String authenticationToken) {
			logger.debug("doFillProjectItems");
			ListBoxModel items = new ListBoxModel();
			for (String projectName : getProjects(serverUrl, authenticationToken)) {
				items.add(projectName);
			}
			return items;
		}

		/** ENVIRONMENT FIELD **/

		public ListBoxModel doFillEnvironmentItems(@QueryParameter("serverUrl") final String serverUrl,
				@QueryParameter("authenticationToken") final String authenticationToken, @QueryParameter("project") final String project) {
			logger.debug("doFillEnvironmentItems");
			ListBoxModel items = new ListBoxModel();
			if (!getProjects(serverUrl, authenticationToken).isEmpty()) {
				List<String> environments;
				try {
					environments = RapidDeployConnector.invokeRapidDeployListEnvironments(authenticationToken, serverUrl, project);
					for (String environmentName : environments) {
						if (!environmentName.contains("Project [") && !environmentName.contains("domainxml")) {
							items.add(environmentName);
						}
					}
				} catch (Exception e) {
					logger.warn(e.getMessage());
				}
			}
			return items;
		}

		/** PACKAGE FIELD **/

		public ListBoxModel doFillPackageNameItems(@QueryParameter("serverUrl") final String serverUrl,
				@QueryParameter("authenticationToken") final String authenticationToken, @QueryParameter("project") final String project,
				@QueryParameter("environment") final String environment) {
			logger.debug("doFillPackageNameItems");
			ListBoxModel items = new ListBoxModel();
			if (!getProjects(serverUrl, authenticationToken).isEmpty()) {
				String[] envObjects = environment.split("\\.");
				List<String> packageNames = new ArrayList<String>();
				try {
					items.add("LATEST");
					if (environment.contains(".") && envObjects.length == 4) {
						packageNames = RapidDeployConnector.invokeRapidDeployListPackages(authenticationToken, serverUrl, project, envObjects[0],
								envObjects[1], envObjects[2]);
					} else if (environment.contains(".") && envObjects.length == 3) {
						// support for RD v3.5+ - instance removed
						packageNames = RapidDeployConnector.invokeRapidDeployListPackages(authenticationToken, serverUrl, project, envObjects[0],
								envObjects[1], null);
					} else {
						logger.error("Invalid environment settings found! Environment: " + environment);
					}
					for (String packageName : packageNames) {
						if (!"null".equals(packageName) && !packageName.startsWith("Deployment")) {
							items.add(packageName);
						}
					}
				} catch (Exception e) {
					logger.warn(e.getMessage());
				}
			}
			return items;
		}

		/** AUX **/

		/** Method to cache the projects to ease the form validation **/
		private synchronized List<String> getProjects(final String serverUrl, final String authenticationToken) {
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
				} catch (Exception e) {
					logger.warn(e.getMessage());
					projects = new ArrayList<String>();
				}
			}
			logger.debug("PROJECTS: " + projects.size());
			return projects;
		}
	}
}
