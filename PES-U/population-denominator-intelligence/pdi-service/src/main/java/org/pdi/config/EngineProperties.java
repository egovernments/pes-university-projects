package org.pdi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pdi.engine")
public record EngineProperties(String python, String workdir, long timeoutSeconds,
                               boolean persistBuildings) {
}
