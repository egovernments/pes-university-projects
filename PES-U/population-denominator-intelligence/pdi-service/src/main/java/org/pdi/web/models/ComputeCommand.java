package org.pdi.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
 * A compute request. Every field except {@code force} identifies the result and so
 * feeds the cache key; {@code force} only controls whether the cached result is
 * consulted, so an ordinary run and a forced recompute share one cache entry.
 */
@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ComputeCommand {

    @JsonProperty("iso3")
    private String iso3;

    @JsonProperty("year")
    private Integer year;

    @JsonProperty("householdSize")
    private Double householdSize;

    @JsonProperty("groups")
    private String groups;

    @JsonProperty("withBuildings")
    private boolean withBuildings;

    @JsonProperty("campaignId")
    private String campaignId;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("force")
    private boolean force;
}
