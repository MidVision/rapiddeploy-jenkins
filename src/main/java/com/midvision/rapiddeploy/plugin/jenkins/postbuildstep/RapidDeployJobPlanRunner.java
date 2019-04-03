package com.midvision.rapiddeploy.plugin.jenkins.postbuildstep;

import java.io.IOException;

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
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

public class RapidDeployJobPlanRunner extends Notifier {

	private final String serverUrl;
	private final String authenticationToken;
	private final String jobPlan;
	private final Boolean asynchronousJob;
	private final Boolean showFullLogs;

	private static final Log logger = LogFactory.getLog(RapidDeployJobPlanRunner.class);

	@DataBoundConstructor
	public RapidDeployJobPlanRunner(final String serverUrl, final String authenticationToken, final String jobPlan, final Boolean asynchronousJob,
			final Boolean showFullLogs) {
		super();
		this.serverUrl = serverUrl;
		this.authenticationToken = authenticationToken;
		this.jobPlan = jobPlan;
		this.asynchronousJob = asynchronousJob;
		this.showFullLogs = showFullLogs;
	}

	@Override
	public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) {
		return RapidDeployConnectorProxy.performJobPlanRun(listener, serverUrl, authenticationToken, jobPlan, asynchronousJob, showFullLogs);
	}

	public String getServerUrl() {
		return serverUrl;
	}

	public String getJobPlan() {
		return jobPlan;
	}

	public String getAuthenticationToken() {
		return authenticationToken;
	}

	public Boolean getAsynchronousJob() {
		return asynchronousJob;
	}

	public Boolean getShowFullLogs() {
		return showFullLogs;
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	/**
	 * Descriptor for {@link RapidDeployJobPlanRunner}. Used as a singleton. The
	 * class is marked as public so that it can be accessed from views.
	 */
	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

		final private static String NOT_EMPTY_MESSAGE = "Please set a value for this field!";
		final private static String NO_PROTOCOL_MESSAGE = "Please specify a protocol for the URL, e.g. \"http://\".";
		final private static String CONNECTION_BAD_MESSAGE = "Unable to establish connection.";

		final private RapidDeployConnectorProxy rdProxy = new RapidDeployConnectorProxy();

		public DescriptorImpl() {
			super(RapidDeployJobPlanRunner.class);
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
			return "RapidDeploy job plan runner";
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

		/** LOAD JOB PLANS BUTTON **/

		public FormValidation doLoadJobPlans(@QueryParameter("serverUrl") final String serverUrl,
				@QueryParameter("authenticationToken") final String authenticationToken) throws IOException, ServletException {
			logger.debug("doLoadJobPlans");
			rdProxy.setNewConnection(true);
			if (rdProxy.getJobPlans(serverUrl, authenticationToken).isEmpty()) {
				return FormValidation.error(CONNECTION_BAD_MESSAGE);
			}
			return FormValidation.ok();
		}

		/** JOB PLANS FIELD **/

		public ListBoxModel doFillJobPlanItems(@QueryParameter("serverUrl") final String serverUrl,
				@QueryParameter("authenticationToken") final String authenticationToken) {
			logger.debug("doFillJobPlans");
			final ListBoxModel items = new ListBoxModel();
			for (final String jobPlan : rdProxy.getJobPlans(serverUrl, authenticationToken)) {
				items.add(jobPlan);
			}
			return items;
		}
	}
}
