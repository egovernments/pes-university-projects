package org.pdi.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
 * A location the engine accepted from an upload.
 */
@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ParsedGeometry {

    @JsonProperty("boundaryCode")
    private String boundaryCode;

    @JsonProperty("geometryType")
    private String geometryType;

    @JsonProperty("source")
    private String source;

    @JsonProperty("geometry")
    private JsonNode geometry;
}
