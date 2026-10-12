package org.pdi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pdi.artifacts")
public record ArtifactProperties(String dir) {
}
