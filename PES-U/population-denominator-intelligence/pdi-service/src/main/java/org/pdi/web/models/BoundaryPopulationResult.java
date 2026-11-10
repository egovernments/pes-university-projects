package org.pdi.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Engine result of a boundary-population pass.
 */
@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BoundaryPopulationResult {

    @JsonProperty("iso3")
    private String iso3;

    @JsonProperty("year")
    private Integer year;

    @JsonProperty("worldpopRelease")
    private String worldpopRelease;

    @JsonProperty("groups")
    private List<String> groups;

    @JsonProperty("results")
    private List<EngineBoundaryPopulation> results;

    @JsonProperty("skipped")
    private List<EngineSkippedBoundary> skipped;
}
