package org.pdi.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

/**
 * Engine population of one boundary code.
 */
@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EngineBoundaryPopulation {

    @JsonProperty("boundaryCode")
    private String boundaryCode;

    @JsonProperty("method")
    private String method;

    @JsonProperty("areaKm2")
    private Double areaKm2;

    @JsonProperty("population")
    private Map<String, Integer> population;
}
