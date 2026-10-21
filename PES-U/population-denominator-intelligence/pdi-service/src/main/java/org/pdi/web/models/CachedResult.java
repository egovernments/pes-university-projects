package org.pdi.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
 * A previously computed engine run, rehydrated from the Postgres result cache.
 * {@code response} is the stored {@code TargetComputeResponse} JSON (its URLs are
 * re-minted against a fresh artifact id on serve); the remaining fields are the
 * artifact file contents, any of which may be {@code null} when that artifact was
 * not produced (e.g. no sheet ⇒ no catchments/buildings/downloadable xlsx).
 */
@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CachedResult {

    @JsonProperty("response")
    private String response;

    @JsonProperty("geojson")
    private String geojson;

    @JsonProperty("statsJson")
    private String statsJson;

    @JsonProperty("settlements")
    private String settlements;

    @JsonProperty("catchments")
    private String catchments;

    @JsonProperty("buildings")
    private String buildings;

    @JsonProperty("sheetXlsx")
    private byte[] sheetXlsx;
}
