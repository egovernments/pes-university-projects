package org.pdi.web.models.hcm;

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
public class EnrichedBoundary {

    @JsonProperty("code")
    private String code;

    @JsonProperty("boundaryType")
    private String boundaryType;

    @JsonProperty("children")
    private List<EnrichedBoundary> children;
}
