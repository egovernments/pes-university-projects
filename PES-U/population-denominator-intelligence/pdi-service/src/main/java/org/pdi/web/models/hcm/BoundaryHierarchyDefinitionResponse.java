package org.pdi.web.models.hcm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * boundary-service {@code boundary-hierarchy-definition/_search} response.
 */
@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BoundaryHierarchyDefinitionResponse {

    @JsonProperty("BoundaryHierarchy")
    private List<BoundaryHierarchyDefinition> boundaryHierarchy;
}
