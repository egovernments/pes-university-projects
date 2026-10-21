package org.pdi.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
 * Read view of a {@code gap_report} row written by the batch engine.
 */
@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GapReport {

    @JsonProperty("id")
    private String id;

    @JsonProperty("boundaryCode")
    private String boundaryCode;

    @JsonProperty("campaignId")
    private String campaignId;

    @JsonProperty("estimatedPopulation")
    private Integer estimatedPopulation;

    @JsonProperty("registeredPopulation")
    private Integer registeredPopulation;

    @JsonProperty("populationGap")
    private Integer populationGap;

    @JsonProperty("estimatedHouseholds")
    private Integer estimatedHouseholds;

    @JsonProperty("registeredHouseholds")
    private Integer registeredHouseholds;

    @JsonProperty("householdGap")
    private Integer householdGap;

    @JsonProperty("coverageRatio")
    private Double coverageRatio;

    @JsonProperty("gapClassification")
    private String gapClassification;

    @JsonProperty("riskScore")
    private Integer riskScore;

    @JsonProperty("riskPriority")
    private String riskPriority;

    @JsonProperty("tenantId")
    private String tenantId;
}
