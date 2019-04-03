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

public class RapidDeployPackageBuilder extends Notifier {

	private final String serverUrl;
	private final String authenticationToken;
	private final String project;
	private final boolean enableCustomPackageName;
	private final String packageName;
	private final String archiveExtension;

	private static final Log logger = LogFactory.getLog(RapidDeployPackageBuilder.class);

	@DataBoundConstructor
	public RapidDeployPackageBuilder(final String serverUrl, final String authenticationToken, final String project, final boolean enableCustomPackageName,
			final String packageName, final String archiveExtension) {
		super();
		this.serverUrl = serverUrl;
		this.authenticationToken = authenticationToken;
		this.project = project;
		this.enableCustomPackageName = enableCustomPackageName;
		this.packageName = packageName;
		this.archiveExtension = archiveExtension;
	}

	@Override
	public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) {
		return RapidDeployConnectorProxy.performPackageBuild(build, listener, serverUrl, authenticationToken, project, getPackageName(), archiveExtension);
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

	public boolean getEnableCustomPackageName() {
		return enableCustomPackageName;
	}

	public String getPackageName() {
		if (enableCustomPackageName) {
			return packageName;
		} else {
			return "";
		}
	}

	public String getArchiveExtension() {
		return archiveExtension;
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	/**
	 * Descriptor for {@link RapidDeployPackageBuilder}. Used as a singleton.
	 * The class is marked as public so that it can be accessed from views.
	 */
	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

		final private static String NOT_EMPTY_MESSAGE = "Please set a value for this field!";
		final private static String NO_PROTOCOL_MESSAGE = "Please specify a protocol for the URL, e.g. \"http://\".";
		final private static String CONNECTION_BAD_MESSAGE = "Unable to establish connection.";
		final private static String WRONG_PROJECT_MESSAGE = "Wrong project selected, please reload the projects list.";

		final private RapidDeployConnectorProxy rdProxy = new RapidDeployConnectorProxy();

		public DescriptorImpl() {
			super(RapidDeployPackageBuilder.class);
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
			return "RapidDeploy deployment package build";
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

		/** SHOW PACKAGES BUTTON **/

		public FormValidation doGetPackages(@QueryParameter("serverUrl") final String serverUrl,
				@QueryParameter("authenticationToken") final String authenticationToken, @QueryParameter("project") final String project)
				throws IOException, ServletException {
			logger.debug("doGetPackages");
			if (rdProxy.getProjects(serverUrl, authenticationToken).contains(project)) {
				final String packagesTable = rdProxy.createPackagesTable(serverUrl, authenticationToken, project);
				if (packagesTable != null) {
					return FormValidation.okWithMarkup(packagesTable);
				}
			} else if (rdProxy.getProjects(serverUrl, authenticationToken).isEmpty()) {
				return FormValidation.error(CONNECTION_BAD_MESSAGE);
			} else {
				return FormValidation.warning(WRONG_PROJECT_MESSAGE);
			}
			return FormValidation.warning("No deployment package could be retrieved for '" + project + "'.");
		}

		/** ARCHIVE EXTENSION FIELD **/

		public ListBoxModel doFillArchiveExtensionItems() {
			logger.debug("doFillArchiveExtensionItems");
			final ListBoxModel items = new ListBoxModel();
			items.add("jar");
			items.add("war");
			items.add("ear");
			items.add("tar");
			items.add("rar");
			items.add("zip");
			return items;
		}
	}
}
