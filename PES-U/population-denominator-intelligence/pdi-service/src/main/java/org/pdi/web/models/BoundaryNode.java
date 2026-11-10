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
 * One boundary of an HCM hierarchy, flattened from boundary-service.
 */
@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BoundaryNode {

    @JsonProperty("code")
    private String code;

    @JsonProperty("boundaryType")
    private String boundaryType;

    @JsonProperty("parentCode")
    private String parentCode;

    @JsonProperty("level")
    private Integer level;

    @JsonProperty("childCodes")
    private List<String> childCodes;
}
