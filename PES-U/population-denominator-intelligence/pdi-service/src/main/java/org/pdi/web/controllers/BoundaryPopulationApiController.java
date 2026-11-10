package org.pdi.web.controllers;

import org.pdi.service.BoundaryPopulationService;
import org.pdi.web.models.BoundaryPopulationSearchRequest;
import org.pdi.web.models.BoundaryPopulationSearchResponse;
import org.pdi.web.models.PopulationRunRequest;
import org.pdi.web.models.PopulationRunResponse;
import org.pdi.web.models.PopulationRunSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping("/population/v1/boundary-population")
@Validated
public class BoundaryPopulationApiController {

    private final BoundaryPopulationService boundaryPopulationService;

    @Autowired
    public BoundaryPopulationApiController(BoundaryPopulationService boundaryPopulationService) {
        this.boundaryPopulationService = boundaryPopulationService;
    }

    /** Start a WorldPop run for a tenant and hierarchy type (DELTA by default). */
    @RequestMapping(value = "/_compute", method = RequestMethod.POST)
    public ResponseEntity<PopulationRunResponse> boundaryPopulationV1ComputePost(@RequestBody PopulationRunRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(boundaryPopulationService.compute(request));
    }

    @RequestMapping(value = "/run/_search", method = RequestMethod.POST)
    public ResponseEntity<PopulationRunResponse> boundaryPopulationV1RunSearchPost(@RequestBody PopulationRunSearchRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(boundaryPopulationService.searchRuns(request));
    }

    /** Population for a list of boundary codes from the active run; called by excel-ingestion. */
    @RequestMapping(value = "/_search", method = RequestMethod.POST)
    public ResponseEntity<BoundaryPopulationSearchResponse> boundaryPopulationV1SearchPost(@RequestBody BoundaryPopulationSearchRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(boundaryPopulationService.search(request));
    }
}
