package com.midvision.rapiddeploy.plugin.jenkins.pipelinedsl;

import java.io.InputStreamReader;
import java.io.Reader;

import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.cps.GlobalVariable;

import groovy.lang.Binding;
import groovy.lang.GroovyCodeSource;

public abstract class PipelineDSLGlobal extends GlobalVariable {

	public abstract String getFunctionName();

	@Override
	public String getName() {
		return getFunctionName();
	}

	@Override
	public Object getValue(final CpsScript script) throws Exception {
		final Binding binding = script.getBinding();

		final CpsThread c = CpsThread.current();
		if (c == null) {
			throw new IllegalStateException("Expected to be called from CpsThread");
		}

		final ClassLoader cl = getClass().getClassLoader();

		final String scriptPath = "dsl/" + getFunctionName() + ".groovy";
		final Reader r = new InputStreamReader(cl.getResourceAsStream(scriptPath), "UTF-8");

		final GroovyCodeSource gsc = new GroovyCodeSource(r, getFunctionName() + ".groovy", cl.getResource(scriptPath).getFile());
		gsc.setCachable(true);

		final Object pipelineDSL = c.getExecution().getShell().getClassLoader().parseClass(gsc).newInstance();
		binding.setVariable(getName(), pipelineDSL);
		r.close();

		return pipelineDSL;
	}
}