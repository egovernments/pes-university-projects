package org.pdi.web.dto;

public record JobStatusResponse(String jobId,
                                String status,
                                String message,
                                Integer percent,
                                TargetComputeResponse result,
                                String error) {
}
