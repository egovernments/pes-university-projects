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
 * The location PDI holds for one HCM boundary code: a point or a polygon.
 */
@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BoundaryGeometry {

    @JsonProperty("id")
    private String id;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("hierarchyType")
    private String hierarchyType;

    @JsonProperty("boundaryCode")
    private String boundaryCode;

    @JsonProperty("geometryType")
    private String geometryType;

    @JsonProperty("geometry")
    private JsonNode geometry;

    @JsonProperty("source")
    private String source;

    @JsonProperty("fileStoreId")
    private String fileStoreId;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails;
}
