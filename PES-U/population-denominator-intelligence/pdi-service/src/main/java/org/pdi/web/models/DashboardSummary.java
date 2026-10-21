package org.pdi.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DashboardSummary {

    @JsonProperty("totalEstimatedPopulation")
    private long totalEstimatedPopulation;

    @JsonProperty("totalRegisteredPopulation")
    private long totalRegisteredPopulation;

    @JsonProperty("totalPopulationGap")
    private long totalPopulationGap;

    @JsonProperty("overallCoverageRatio")
    private Double overallCoverageRatio;

    @JsonProperty("totalEstimatedHouseholds")
    private long totalEstimatedHouseholds;

    @JsonProperty("totalRegisteredHouseholds")
    private long totalRegisteredHouseholds;

    @JsonProperty("householdGap")
    private long householdGap;

    @JsonProperty("invisibleSettlementCount")
    private long invisibleSettlementCount;

    @JsonProperty("invisibleEstimatedPopulation")
    private long invisibleEstimatedPopulation;
}
