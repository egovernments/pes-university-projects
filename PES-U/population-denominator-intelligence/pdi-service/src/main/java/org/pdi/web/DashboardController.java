package org.pdi.web;

import org.pdi.service.DashboardService;
import org.pdi.web.dto.DashboardStatsResponse;
import org.pdi.web.dto.GapSearchRequest;
import org.pdi.web.dto.GapSearchResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only dashboard API over the pre-computed PostGIS tables. Endpoints follow
 * the documented DIGIT shape (ARCHITECTURE.md §8.1 / §14.2) and are tenant-scoped.
 */
@RestController
@RequestMapping("/population/v1")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard/_stats")
    public DashboardStatsResponse stats(@RequestParam("campaignId") String campaignId,
                                        @RequestParam(value = "boundaryCode", required = false) String boundaryCode,
                                        @RequestParam(value = "tenantId", defaultValue = "default") String tenantId) {
        if (campaignId == null || campaignId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "campaignId is required");
        }
        return dashboardService.stats(campaignId, boundaryCode, tenantId);
    }

    @PostMapping("/gap/_search")
    public GapSearchResponse gapSearch(@RequestBody GapSearchRequest request,
                                       @RequestParam(value = "tenantId", defaultValue = "default") String tenantId) {
        if (request == null || request.gapSearchCriteria() == null
                || request.gapSearchCriteria().campaignId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "gapSearchCriteria.campaignId is required");
        }
        return dashboardService.search(request.gapSearchCriteria(), tenantId);
    }
}
