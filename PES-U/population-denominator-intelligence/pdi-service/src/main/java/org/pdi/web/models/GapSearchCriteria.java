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
public class GapSearchCriteria {

    @JsonProperty("campaignId")
    private String campaignId;

    @JsonProperty("boundaryCode")
    private String boundaryCode;

    @JsonProperty("boundaryType")
    private String boundaryType;

    @JsonProperty("gapClassification")
    private List<String> gapClassification;

    @JsonProperty("limit")
    private Integer limit;

    @JsonProperty("offset")
    private Integer offset;

    @JsonProperty("sortBy")
    private String sortBy;

    @JsonProperty("sortOrder")
    private String sortOrder;
}
