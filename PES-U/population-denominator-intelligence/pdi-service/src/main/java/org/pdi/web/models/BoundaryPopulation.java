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
 * WorldPop population of one HCM boundary within a run.
 */
@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BoundaryPopulation {

    @JsonProperty("id")
    private String id;

    @JsonProperty("runId")
    private String runId;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("hierarchyType")
    private String hierarchyType;

    @JsonProperty("boundaryCode")
    private String boundaryCode;

    @JsonProperty("boundaryType")
    private String boundaryType;

    @JsonProperty("parentBoundaryCode")
    private String parentBoundaryCode;

    @JsonProperty("population")
    private Map<String, Integer> population;

    @JsonProperty("method")
    private String method;

    @JsonProperty("areaKm2")
    private Double areaKm2;

    @JsonProperty("createdTime")
    private Long createdTime;
}
