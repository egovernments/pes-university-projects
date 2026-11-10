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
 * Engine result of checking a geometry upload.
 */
@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GeometryParseResult {

    @JsonProperty("accepted")
    private List<ParsedGeometry> accepted;

    @JsonProperty("rejected")
    private List<GeometryRejection> rejected;

    @JsonProperty("warnings")
    private List<GeometryWarning> warnings;
}
