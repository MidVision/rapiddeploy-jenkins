package com.midvision.rapiddeploy.plugin.jenkins.log;

import java.io.PrintStream;

public class JobLogger implements Logger {

    private final PrintStream logger;

    public JobLogger(final PrintStream logger) {
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
