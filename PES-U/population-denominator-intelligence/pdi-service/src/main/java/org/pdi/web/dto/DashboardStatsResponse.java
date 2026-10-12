package org.pdi.web.dto;

import java.util.List;
import java.util.Map;

/**
 * Payload for {@code GET /population/v1/dashboard/_stats}, shaped exactly as
 * documented in ARCHITECTURE.md §14.2.
 */
public record DashboardStatsResponse(String campaignId,
                                     String boundaryCode,
                                     Summary summary,
                                     Map<String, GapBucket> gapDistribution,
                                     List<TopGapSettlement> topGapSettlements,
                                     List<CoverageEntry> coverageBySubBoundary,
                                     Map<String, Long> riskDistribution) {

    public record Summary(long totalEstimatedPopulation,
                          long totalRegisteredPopulation,
                          long totalPopulationGap,
                          Double overallCoverageRatio,
                          long totalEstimatedHouseholds,
                          long totalRegisteredHouseholds,
                          long householdGap,
                          long invisibleSettlementCount,
                          long invisibleEstimatedPopulation) {
    }

    public record GapBucket(long count, long population) {
    }

    public record TopGapSettlement(String name,
                                   String boundaryCode,
                                   long gap,
                                   Double coverageRatio,
                                   Integer riskScore,
                                   String riskPriority) {
    }

    public record CoverageEntry(String name, String boundaryCode, Double coverageRatio) {
    }
}
