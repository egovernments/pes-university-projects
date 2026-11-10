package org.pdi.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GeometrySheetCriteria {

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("hierarchyType")
    private String hierarchyType;

    @JsonProperty("onlyMissing")
    private Boolean onlyMissing;

    @JsonProperty("locale")
    private String locale;
}
