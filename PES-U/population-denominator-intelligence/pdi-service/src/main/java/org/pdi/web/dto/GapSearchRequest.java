package org.pdi.web.dto;

import java.util.List;

/**
 * Body for {@code POST /population/v1/gap/_search}, per ARCHITECTURE.md §8.1.
 */
public record GapSearchRequest(GapSearchCriteria gapSearchCriteria) {

    public record GapSearchCriteria(String campaignId,
                                    String boundaryCode,
                                    String boundaryType,
                                    List<String> gapClassification,
                                    Integer limit,
                                    Integer offset,
                                    String sortBy,
                                    String sortOrder) {
    }
}
