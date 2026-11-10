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
public class BoundaryGeometrySearchResponse {

    @JsonProperty("ResponseInfo")
    private ResponseInfo responseInfo;

    @JsonProperty("BoundaryGeometry")
    private List<BoundaryGeometry> boundaryGeometry;

    @JsonProperty("MissingBoundaryCodes")
    private List<String> missingBoundaryCodes;

    @JsonProperty("locatedCount")
    private Integer locatedCount;

    @JsonProperty("missingCount")
    private Integer missingCount;
}
