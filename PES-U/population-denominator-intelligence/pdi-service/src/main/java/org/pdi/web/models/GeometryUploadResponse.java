package org.pdi.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GeometryUploadResponse {

    @JsonProperty("ResponseInfo")
    private ResponseInfo responseInfo;

    @JsonProperty("uploadId")
    private String uploadId;

    @JsonProperty("acceptedCount")
    private Integer acceptedCount;

    @JsonProperty("rejectedCount")
    private Integer rejectedCount;

    @JsonProperty("Rejected")
    private List<GeometryRejection> rejected;

    @JsonProperty("Warnings")
    private List<GeometryWarning> warnings;

    @JsonProperty("errorSheetUrl")
    private String errorSheetUrl;

    @JsonProperty("PopulationRun")
    private PopulationRun populationRun;
}
