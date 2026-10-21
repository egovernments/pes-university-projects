package org.pdi.web.models;

import lombok.Getter;

import java.nio.file.Path;

/**
 * An in-flight or finished compute run. Held in memory only; the status endpoint polls it.
 */
@Getter
public class Job {

    public enum Status { RUNNING, DONE, FAILED }

    private final String id;

    private final Path logFile;

    private volatile Status status = Status.RUNNING;

    private volatile TargetComputeResponse result;

    private volatile String error;

    public Job(String id, Path logFile) {
        this.id = id;
        this.logFile = logFile;
    }

    public void complete(TargetComputeResponse computed) {
        this.result = computed;
        this.status = Status.DONE;
    }

    public void fail(String message) {
        this.error = message;
        this.status = Status.FAILED;
    }
}
