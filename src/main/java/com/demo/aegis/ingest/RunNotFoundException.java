package com.demo.aegis.ingest;

public class RunNotFoundException extends RuntimeException {

    public RunNotFoundException(long runId) {
        super("Run " + runId + " does not exist");
    }
}
