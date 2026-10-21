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
public class GapReportItem {

    @JsonProperty("settlementId")
    private String settlementId;

    @JsonProperty("boundaryCode")
    private String boundaryCode;

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
}
