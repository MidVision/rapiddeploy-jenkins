package com.midvision.rapiddeploy.plugin.jenkins.buildstep;

import java.io.IOException;

import javax.servlet.ServletException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import com.midvision.rapiddeploy.plugin.jenkins.RapidDeployConnectorProxy;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

public class RapidDeployJobPlanRunner extends Builder {

	private final String serverUrl;
	private final String authenticationToken;
	private final String jobPlan;
	private final Boolean asynchronousJob;
	private final Boolean showIndividualLogs;
	private final Boolean showFullLog;

	private static final Log logger = LogFactory.getLog(RapidDeployJobPlanRunner.class);

	@DataBoundConstructor
	public RapidDeployJobPlanRunner(final String serverUrl, final String authenticationToken, final String jobPlan, final Boolean asynchronousJob,
			final Boolean showIndividualLogs, final Boolean showFullLog) {
		super();
		this.serverUrl = serverUrl;
		this.authenticationToken = authenticationToken;
		this.jobPlan = jobPlan;
		this.asynchronousJob = asynchronousJob;
		this.showIndividualLogs = showIndividualLogs;
		this.showFullLog = showFullLog;
	}

	@Override
	public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) {
		return RapidDeployConnectorProxy.performJobPlanRun(listener, serverUrl, authenticationToken, jobPlan, asynchronousJob, showIndividualLogs, showFullLog);
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

	public Boolean getShowIndividualLogs() {
		return showIndividualLogs;
	}

	public Boolean getShowFullLog() {
		return showFullLog;
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	/**
	 * Descriptor for {@link RapidDeployJobPlanRunner}. Used as a singleton. The class is marked as public so that it can be accessed from views.
	 */
	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

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
		@RequirePOST
		public FormValidation doCheckServerUrl(@QueryParameter final String value, @AncestorInPath final Item item) throws IOException, ServletException {
			logger.debug("doCheckServerUrl");
			if (item == null) {
				return FormValidation.ok();
			}
			if (!item.hasPermission(Item.CONFIGURE)) {
				return FormValidation.warning(RapidDeployConnectorProxy.INSUFFICIENT_PERMISSIONS_MESSAGE);
			}
			rdProxy.setNewConnection(true);
			if (value.length() == 0) {
				return FormValidation.error(RapidDeployConnectorProxy.NOT_EMPTY_MESSAGE);
			} else if (!value.startsWith("http://") && !value.startsWith("https://")) {
				return FormValidation.warning(RapidDeployConnectorProxy.NO_PROTOCOL_MESSAGE);
			}
			return FormValidation.ok();
		}

		/** AUTHENTICATION TOKEN FIELD **/
		@RequirePOST
		public FormValidation doCheckAuthenticationToken(@QueryParameter final String value, @AncestorInPath final Item item)
				throws IOException, ServletException {
			logger.debug("doCheckAuthenticationToken");
			if (item == null) {
				return FormValidation.ok();
			}
			if (!item.hasPermission(Item.CONFIGURE)) {
				return FormValidation.warning(RapidDeployConnectorProxy.INSUFFICIENT_PERMISSIONS_MESSAGE);
			}
			rdProxy.setNewConnection(true);
			if (value.length() == 0) {
				return FormValidation.error(RapidDeployConnectorProxy.NOT_EMPTY_MESSAGE);
			}
			return FormValidation.ok();
		}

		/** LOAD JOB PLANS BUTTON **/
		@RequirePOST
		public FormValidation doLoadJobPlans(@QueryParameter("serverUrl") final String serverUrl,
				@QueryParameter("authenticationToken") final String authenticationToken, @AncestorInPath final Item item) throws IOException, ServletException {
			logger.debug("doLoadJobPlans");
			if (item == null) {
				return FormValidation.ok();
			}
			if (!item.hasPermission(Item.CONFIGURE)) {
				return FormValidation.warning(RapidDeployConnectorProxy.INSUFFICIENT_PERMISSIONS_MESSAGE);
			}
			rdProxy.setNewConnection(true);
			if (rdProxy.getJobPlans(serverUrl, authenticationToken).isEmpty()) {
				return FormValidation.error(RapidDeployConnectorProxy.CONNECTION_BAD_MESSAGE);
			}
			return FormValidation.ok();
		}

		/** JOB PLANS FIELD **/
		@RequirePOST
		public ListBoxModel doFillJobPlanItems(@QueryParameter("serverUrl") final String serverUrl,
				@QueryParameter("authenticationToken") final String authenticationToken, @AncestorInPath final Item item) {
			logger.debug("doFillJobPlans");
			final ListBoxModel listBoxItems = new ListBoxModel();
			if (item == null) {
				return listBoxItems;
			}
			if (!item.hasPermission(Item.CONFIGURE)) {
				return listBoxItems.add("No items retrieved. " + RapidDeployConnectorProxy.INSUFFICIENT_PERMISSIONS_MESSAGE);
			}
			for (final String jobPlan : rdProxy.getJobPlans(serverUrl, authenticationToken)) {
				listBoxItems.add(jobPlan);
			}
			return listBoxItems;
		}
	}
}
