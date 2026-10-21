package org.pdi.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;

/**
 * Payload for {@code GET /population/v1/dashboard/_stats}, shaped exactly as
 * documented in ARCHITECTURE.md §14.2.
 */
@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DashboardStatsResponse {

    @JsonProperty("campaignId")
    private String campaignId;

    @JsonProperty("boundaryCode")
    private String boundaryCode;

    @JsonProperty("summary")
    private DashboardSummary summary;

    @JsonProperty("gapDistribution")
    private Map<String, GapBucket> gapDistribution;

    @JsonProperty("topGapSettlements")
    private List<TopGapSettlement> topGapSettlements;

    @JsonProperty("coverageBySubBoundary")
    private List<CoverageEntry> coverageBySubBoundary;

    @JsonProperty("riskDistribution")
    private Map<String, Long> riskDistribution;
}
