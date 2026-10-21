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
 * Response for {@code POST /population/v1/gap/_search}, per ARCHITECTURE.md §8.1.
 */
@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GapSearchResponse {

    @JsonProperty("gapReports")
    private List<GapReportItem> gapReports;

    @JsonProperty("totalCount")
    private long totalCount;

    @JsonProperty("pagination")
    private Pagination pagination;
}
