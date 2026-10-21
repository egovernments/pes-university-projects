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
 * Result document the Python engine writes with {@code --json}.
 */
@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EngineResult {

    @JsonProperty("iso3")
    private String iso3;

    @JsonProperty("count")
    private int count;

    @JsonProperty("groups")
    private List<String> groups;

    @JsonProperty("sheet")
    private String sheet;

    @JsonProperty("geojson")
    private String geojson;

    @JsonProperty("targets")
    private List<Map<String, Object>> targets;

    @JsonProperty("registeredAvailable")
    private boolean registeredAvailable;

    @JsonProperty("registered")
    private List<Map<String, Object>> registered;

    @JsonProperty("invisibleAvailable")
    private boolean invisibleAvailable;

    @JsonProperty("invisibleCount")
    private int invisibleCount;

    @JsonProperty("invisibleGeojson")
    private String invisibleGeojson;

    @JsonProperty("catchmentsAvailable")
    private boolean catchmentsAvailable;

    @JsonProperty("catchmentCount")
    private int catchmentCount;

    @JsonProperty("catchmentsGeojson")
    private String catchmentsGeojson;

    @JsonProperty("buildingsGeojson")
    private String buildingsGeojson;

    @JsonProperty("riskAvailable")
    private boolean riskAvailable;

    @JsonProperty("statsJson")
    private String statsJson;

    // How the uploads resolved: name matches, misses, cells needing review.
    @JsonProperty("provenance")
    private Map<String, Object> provenance;
}
