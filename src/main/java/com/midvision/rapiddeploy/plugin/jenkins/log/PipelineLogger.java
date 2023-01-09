package com.midvision.rapiddeploy.plugin.jenkins.log;

import groovy.lang.Script;

public class PipelineLogger implements Logger {

    private final Script logger;

    public PipelineLogger(final Script logger) {
        this.logger = logger;
    }

    @Override
    public void println() {
        logger.println(System.lineSeparator());
    }

    @Override
    public void println(final Object message) {
        logger.println(message);
    }
}
