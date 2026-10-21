package org.pdi.web.controllers;

import org.pdi.service.DashboardService;
import org.pdi.web.models.DashboardStatsResponse;
import org.pdi.web.models.GapSearchRequest;
import org.pdi.web.models.GapSearchResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Read-only dashboard API over the pre-computed PostGIS tables. Endpoints follow
 * the documented DIGIT shape (ARCHITECTURE.md §8.1 / §14.2) and are tenant-scoped.
 */
@Controller
@RequestMapping("/population/v1")
@Validated
public class DashboardApiController {

    private final DashboardService dashboardService;

    @Autowired
    public DashboardApiController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @RequestMapping(value = "/dashboard/_stats", method = RequestMethod.GET)
    public ResponseEntity<DashboardStatsResponse> dashboardV1StatsGet(@RequestParam("campaignId") String campaignId,
                                                                      @RequestParam(value = "boundaryCode", required = false) String boundaryCode,
                                                                      @RequestParam(value = "tenantId", defaultValue = "default") String tenantId) {
        DashboardStatsResponse response = dashboardService.stats(campaignId, boundaryCode, tenantId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @RequestMapping(value = "/gap/_search", method = RequestMethod.POST)
    public ResponseEntity<GapSearchResponse> gapV1SearchPost(@RequestBody GapSearchRequest request,
                                                             @RequestParam(value = "tenantId", defaultValue = "default") String tenantId) {
        GapSearchResponse response = dashboardService.search(request, tenantId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
