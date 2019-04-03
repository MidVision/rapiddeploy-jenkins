package com.midvision.rapiddeploy.plugin.jenkins.postbuildstep;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.midvision.rapiddeploy.plugin.jenkins.RapidDeployConnectorProxy;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

public class RapidDeployJobRunner extends Notifier {

	private final String serverUrl;
	private final String authenticationToken;
	private final String project;
	private final String target;
	private final String packageName;
	private final Boolean asynchronousJob;

	private static final Log logger = LogFactory.getLog(RapidDeployJobRunner.class);

	@DataBoundConstructor
	public RapidDeployJobRunner(final String serverUrl, final String authenticationToken, final String project, final String target, final String packageName,
			final Boolean asynchronousJob) {
		super();
		this.serverUrl = serverUrl;
		this.authenticationToken = authenticationToken;
		this.target = target;
		this.packageName = packageName;
		this.project = project;
		this.asynchronousJob = asynchronousJob;
	}

	@Override
	public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) {
		return RapidDeployConnectorProxy.performJobDeployment(build, listener, serverUrl, authenticationToken, project, target, packageName, asynchronousJob);
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

	public String getTarget() {
		return target;
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

		final private RapidDeployConnectorProxy rdProxy = new RapidDeployConnectorProxy();

		public DescriptorImpl() {
			super(RapidDeployJobRunner.class);
			load();
		}

		@SuppressWarnings("rawtypes")
		@Override
		public boolean isApplicable(final Class<? extends AbstractProject> aClass) {
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

		public FormValidation doCheckServerUrl(@QueryParameter final String value) throws IOException, ServletException {
			logger.debug("doCheckServerUrl");
			rdProxy.setNewConnection(true);
			if (value.length() == 0) {
				return FormValidation.error(NOT_EMPTY_MESSAGE);
			} else if (!value.startsWith("http://") && !value.startsWith("https://")) {
				return FormValidation.warning(NO_PROTOCOL_MESSAGE);
			}
			return FormValidation.ok();
		}

		/** AUTHENTICATION TOKEN FIELD **/

		public FormValidation doCheckAuthenticationToken(@QueryParameter final String value) throws IOException, ServletException {
			logger.debug("doCheckAuthenticationToken");
			rdProxy.setNewConnection(true);
			if (value.length() == 0) {
				return FormValidation.error(NOT_EMPTY_MESSAGE);
			}
			return FormValidation.ok();
		}

		/** LOAD PROJECTS BUTTON **/

		public FormValidation doLoadProjects(@QueryParameter("serverUrl") final String serverUrl,
				@QueryParameter("authenticationToken") final String authenticationToken) throws IOException, ServletException {
			logger.debug("doLoadProjects");
			rdProxy.setNewConnection(true);
			if (rdProxy.getProjects(serverUrl, authenticationToken).isEmpty()) {
				return FormValidation.error(CONNECTION_BAD_MESSAGE);
			}
			return FormValidation.ok();
		}

		/** PROJECT FIELD **/

		public ListBoxModel doFillProjectItems(@QueryParameter("serverUrl") final String serverUrl,
				@QueryParameter("authenticationToken") final String authenticationToken) {
			logger.debug("doFillProjectItems");
			final ListBoxModel items = new ListBoxModel();
			for (final String projectName : rdProxy.getProjects(serverUrl, authenticationToken)) {
				items.add(projectName);
			}
			return items;
		}

		/** TARGET FIELD **/

		public ListBoxModel doFillTargetItems(@QueryParameter("serverUrl") final String serverUrl,
				@QueryParameter("authenticationToken") final String authenticationToken, @QueryParameter("project") final String project) {
			logger.debug("doFillTargetItems");
			final ListBoxModel items = new ListBoxModel();
			if (!rdProxy.getProjects(serverUrl, authenticationToken).isEmpty()) {
				try {
					final List<String> targets = rdProxy.getTargets(serverUrl, authenticationToken, project);
					for (final String targetName : targets) {
						if (!targetName.contains("Project [") && !targetName.contains("domainxml")) {
							items.add(targetName);
						}
					}
				} catch (final Exception e) {
					logger.warn(e.getMessage());
				}
			}
			return items;
		}

		/** PACKAGE FIELD **/

		public ComboBoxModel doFillPackageNameItems(@QueryParameter("serverUrl") final String serverUrl,
				@QueryParameter("authenticationToken") final String authenticationToken, @QueryParameter("project") final String project,
				@QueryParameter("target") final String target) {
			logger.debug("doFillPackageNameItems");
			final ComboBoxModel items = new ComboBoxModel();
			if (!rdProxy.getProjects(serverUrl, authenticationToken).isEmpty()) {
				try {
					items.add("LATEST");
					final List<String> packageNames = rdProxy.getDeploymentPackages(serverUrl, authenticationToken, project, target);
					for (final String packageName : packageNames) {
						if (!"null".equals(packageName) && !packageName.startsWith("Deployment")) {
							items.add(packageName);
						}
					}
				} catch (final Exception e) {
					logger.warn(e.getMessage());
				}
			}
			return items;
		}
	}
}
