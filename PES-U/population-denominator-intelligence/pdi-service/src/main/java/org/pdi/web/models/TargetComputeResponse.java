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

@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TargetComputeResponse {

    @JsonProperty("jobId")
    private String jobId;

    @JsonProperty("iso3")
    private String iso3;

    @JsonProperty("boundaryCount")
    private int boundaryCount;

    @JsonProperty("groups")
    private List<String> groups;

    @JsonProperty("targets")
    private List<BoundaryTarget> targets;

    @JsonProperty("registeredAvailable")
    private boolean registeredAvailable;

    @JsonProperty("registered")
    private List<BoundaryTarget> registered;

    @JsonProperty("invisibleAvailable")
    private boolean invisibleAvailable;

    @JsonProperty("invisibleCount")
    private int invisibleCount;

    @JsonProperty("catchmentsAvailable")
    private boolean catchmentsAvailable;

    @JsonProperty("catchmentCount")
    private int catchmentCount;

    @JsonProperty("riskAvailable")
    private boolean riskAvailable;

    @JsonProperty("downloadUrl")
    private String downloadUrl;

    @JsonProperty("geojsonUrl")
    private String geojsonUrl;

    @JsonProperty("settlementsUrl")
    private String settlementsUrl;

    @JsonProperty("catchmentsUrl")
    private String catchmentsUrl;

    @JsonProperty("buildingsUrl")
    private String buildingsUrl;

    @JsonProperty("statsUrl")
    private String statsUrl;

    @JsonProperty("provenance")
    private Map<String, Object> provenance;
}
