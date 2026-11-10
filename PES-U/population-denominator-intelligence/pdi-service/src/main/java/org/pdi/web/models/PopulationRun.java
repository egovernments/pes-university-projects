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
 * One WorldPop calculation for a tenant and hierarchy type.
 */
@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PopulationRun {

    @JsonProperty("id")
    private String id;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("hierarchyType")
    private String hierarchyType;

    @JsonProperty("iso3")
    private String iso3;

    @JsonProperty("year")
    private Integer year;

    @JsonProperty("worldpopRelease")
    private String worldpopRelease;

    @JsonProperty("groups")
    private List<String> groups;

    @JsonProperty("mode")
    private String mode;

    @JsonProperty("status")
    private String status;

    @JsonProperty("isActive")
    private Boolean isActive;

    @JsonProperty("activateOnSuccess")
    private Boolean activateOnSuccess;

    @JsonProperty("progress")
    private Integer progress;

    @JsonProperty("boundaryCount")
    private Integer boundaryCount;

    @JsonProperty("computedCount")
    private Integer computedCount;

    @JsonProperty("noGeometryCount")
    private Integer noGeometryCount;

    @JsonProperty("errorMessage")
    private String errorMessage;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails;
}
