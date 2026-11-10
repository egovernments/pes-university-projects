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
 * Parameters of a geometry sheet upload.
 */
@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GeometryUploadCommand {

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("hierarchyType")
    private String hierarchyType;

    @JsonProperty("iso3")
    private String iso3;

    @JsonProperty("year")
    private Integer year;

    @JsonProperty("groups")
    private List<String> groups;

    @JsonProperty("startRun")
    private boolean startRun;

    @JsonProperty("RequestInfo")
    private RequestInfo requestInfo;
}
