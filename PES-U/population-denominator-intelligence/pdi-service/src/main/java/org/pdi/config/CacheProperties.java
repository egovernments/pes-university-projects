package org.pdi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for the Postgres-backed engine result cache (pdi.cache.*). */
@ConfigurationProperties(prefix = "pdi.cache")
public record CacheProperties(boolean enabled) {
}
