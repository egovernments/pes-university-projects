package org.pdi.engine;

/**
 * A compute request. Every field except {@code force} identifies the result and so
 * feeds the cache key; {@code force} only controls whether the cached result is
 * consulted, so an ordinary run and a forced recompute share one cache entry.
 */
public record ComputeCommand(String iso3, Integer year, Double householdSize, String groups,
                             boolean withBuildings, String campaignId, String tenantId,
                             boolean force) {
}
