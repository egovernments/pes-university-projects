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
public class TopGapSettlement {

    @JsonProperty("name")
    private String name;

    @JsonProperty("boundaryCode")
    private String boundaryCode;

    @JsonProperty("gap")
    private long gap;

    @JsonProperty("coverageRatio")
    private Double coverageRatio;

    @JsonProperty("riskScore")
    private Integer riskScore;

    @JsonProperty("riskPriority")
    private String riskPriority;
}
