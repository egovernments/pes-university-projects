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
public class PopulationRunSearchCriteria {

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("hierarchyType")
    private String hierarchyType;

    @JsonProperty("ids")
    private List<String> ids;

    @JsonProperty("status")
    private String status;

    @JsonProperty("isActive")
    private Boolean isActive;

    @JsonProperty("limit")
    private Integer limit;

    @JsonProperty("offset")
    private Integer offset;
}
