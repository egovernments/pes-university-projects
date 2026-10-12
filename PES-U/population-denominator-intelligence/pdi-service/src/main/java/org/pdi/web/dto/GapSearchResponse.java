package org.pdi.web.dto;

import java.util.List;

/**
 * Response for {@code POST /population/v1/gap/_search}, per ARCHITECTURE.md §8.1.
 */
public record GapSearchResponse(List<GapReportItem> gapReports,
                                long totalCount,
                                Pagination pagination) {

    public record GapReportItem(String settlementId,
                                String boundaryCode,
                                Integer estimatedPopulation,
                                Integer registeredPopulation,
                                Integer populationGap,
                                Integer estimatedHouseholds,
                                Integer registeredHouseholds,
                                Integer householdGap,
                                Double coverageRatio,
                                String gapClassification,
                                Integer riskScore,
                                String riskPriority) {
    }

    public record Pagination(int limit, int offset) {
    }
}
