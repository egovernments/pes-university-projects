package org.pdi.service;

import org.pdi.web.dto.TargetComputeResponse;

import java.nio.file.Path;

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

    public String id() {
        return id;
    }

    public Path logFile() {
        return logFile;
    }

    public Status status() {
        return status;
    }

    public TargetComputeResponse result() {
        return result;
    }

    public String error() {
        return error;
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
