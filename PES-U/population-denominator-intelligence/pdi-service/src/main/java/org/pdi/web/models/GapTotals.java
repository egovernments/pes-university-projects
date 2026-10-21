package org.pdi.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
 * Population and household sums over the {@code gap_report} rows of one campaign.
 */
@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GapTotals {

    @JsonProperty("estimatedPopulation")
    private long estimatedPopulation;

    @JsonProperty("registeredPopulation")
    private long registeredPopulation;

    @JsonProperty("estimatedHouseholds")
    private long estimatedHouseholds;

    @JsonProperty("registeredHouseholds")
    private long registeredHouseholds;
}
