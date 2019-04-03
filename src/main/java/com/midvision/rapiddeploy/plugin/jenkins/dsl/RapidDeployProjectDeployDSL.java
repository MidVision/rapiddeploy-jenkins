package com.midvision.rapiddeploy.plugin.jenkins.dsl;

import java.io.IOException;

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.ProxyWhitelist;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.StaticWhitelist;

import com.midvision.rapiddeploy.plugin.jenkins.pipelinedsl.PipelineDSLGlobal;

import hudson.Extension;

@Extension
public class RapidDeployProjectDeployDSL extends PipelineDSLGlobal {

	@Override
	public String getFunctionName() {
		return "rdProjectDeploy";
	}

	@Extension
	public static class MiscWhitelist extends ProxyWhitelist {
		public MiscWhitelist() throws IOException {
			super(new StaticWhitelist("method java.util.Map$Entry getKey", "method java.util.Map$Entry getValue"));
		}
	}
}