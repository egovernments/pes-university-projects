package org.pdi.web.controllers;

import org.pdi.service.TargetService;
import org.pdi.web.models.ComputeCommand;
import org.pdi.web.models.EngineInput;
import org.pdi.web.models.JobStatusResponse;
import org.pdi.web.models.JobSubmitResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

import static org.pdi.util.PdiConstants.BOUNDARY_GEOJSON_NOT_FOUND_MSG;
import static org.pdi.util.PdiConstants.BUILDINGS_EXTENSION;
import static org.pdi.util.PdiConstants.BUILDINGS_NOT_FOUND_MSG;
import static org.pdi.util.PdiConstants.CATCHMENTS_EXTENSION;
import static org.pdi.util.PdiConstants.CATCHMENTS_NOT_FOUND_MSG;
import static org.pdi.util.PdiConstants.GEOJSON_EXTENSION;
import static org.pdi.util.PdiConstants.SETTLEMENTS_EXTENSION;
import static org.pdi.util.PdiConstants.SETTLEMENTS_NOT_FOUND_MSG;
import static org.pdi.util.PdiConstants.STATS_EXTENSION;
import static org.pdi.util.PdiConstants.STATS_NOT_FOUND_MSG;
import static org.pdi.util.PdiConstants.STATUS_PATH;
import static org.pdi.util.PdiConstants.TARGETS_BASE_PATH;
import static org.pdi.util.PdiConstants.TARGET_SHEET_NOT_FOUND_MSG;
import static org.pdi.util.PdiConstants.XLSX_EXTENSION;
import static org.pdi.util.PdiConstants.XLSX_MEDIA_TYPE;

@Controller
@RequestMapping("/population/v1/targets")
@Validated
public class TargetApiController {

    private final TargetService targetService;

    @Autowired
    public TargetApiController(TargetService targetService) {
        this.targetService = targetService;
    }

    /**
     * Start a compute run.
     *
     * <p>{@code boundaries} is the catchment geojson and {@code enumeration} the field
     * workbook; both are optional, and each one supplied adds a layer to the result. The
     * older {@code sheet} parameter still works but is superseded by {@code boundaries}.
     */
    @RequestMapping(value = "/_compute", method = RequestMethod.POST, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JobSubmitResponse> targetsV1ComputePost(@RequestParam(value = "boundaries", required = false) MultipartFile boundaries,
                                                                  @RequestParam(value = "enumeration", required = false) MultipartFile enumeration,
                                                                  @RequestParam(value = "sheet", required = false) MultipartFile sheet,
                                                                  @RequestParam("iso3") String iso3,
                                                                  @RequestParam(value = "year", required = false) Integer year,
                                                                  @RequestParam(value = "householdSize", required = false) Double householdSize,
                                                                  @RequestParam(value = "groups", required = false) String groups,
                                                                  @RequestParam(value = "withBuildings", defaultValue = "true") boolean withBuildings,
                                                                  @RequestParam(value = "campaignId", required = false) String campaignId,
                                                                  @RequestParam(value = "tenantId", defaultValue = "default") String tenantId,
                                                                  @RequestParam(value = "force", defaultValue = "false") boolean force) {
        Map<EngineInput, MultipartFile> files = new EnumMap<>(EngineInput.class);
        files.put(EngineInput.BOUNDARIES, boundaries);
        files.put(EngineInput.ENUMERATION, enumeration);
        files.put(EngineInput.SHEET, sheet);

        ComputeCommand command = ComputeCommand.builder()
                .iso3(iso3)
                .year(year)
                .householdSize(householdSize)
                .groups(groups)
                .withBuildings(withBuildings)
                .campaignId(campaignId)
                .tenantId(tenantId)
                .force(force)
                .build();
        String jobId = targetService.submit(files, command);
        JobSubmitResponse response = JobSubmitResponse.builder()
                .jobId(jobId)
                .statusUrl(TARGETS_BASE_PATH + jobId + STATUS_PATH)
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @RequestMapping(value = "/{jobId}/_status", method = RequestMethod.GET)
    public ResponseEntity<JobStatusResponse> targetsV1StatusGet(@PathVariable String jobId) {
        return ResponseEntity.status(HttpStatus.OK).body(targetService.getJobStatus(jobId));
    }

    @RequestMapping(value = "/{jobId}/_download", method = RequestMethod.GET)
    public ResponseEntity<Resource> targetsV1DownloadGet(@PathVariable String jobId) {
        Path sheet = targetService.getArtifact(jobId, XLSX_EXTENSION, TARGET_SHEET_NOT_FOUND_MSG);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"targets-" + jobId + ".xlsx\"")
                .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
                .body(new PathResource(sheet));
    }

    @RequestMapping(value = "/{jobId}/_geojson", method = RequestMethod.GET)
    public ResponseEntity<Resource> targetsV1GeojsonGet(@PathVariable String jobId) {
        return jsonArtifact(targetService.getArtifact(jobId, GEOJSON_EXTENSION, BOUNDARY_GEOJSON_NOT_FOUND_MSG));
    }

    @RequestMapping(value = "/{jobId}/_settlements", method = RequestMethod.GET)
    public ResponseEntity<Resource> targetsV1SettlementsGet(@PathVariable String jobId) {
        return jsonArtifact(targetService.getArtifact(jobId, SETTLEMENTS_EXTENSION, SETTLEMENTS_NOT_FOUND_MSG));
    }

    @RequestMapping(value = "/{jobId}/_catchments", method = RequestMethod.GET)
    public ResponseEntity<Resource> targetsV1CatchmentsGet(@PathVariable String jobId) {
        return jsonArtifact(targetService.getArtifact(jobId, CATCHMENTS_EXTENSION, CATCHMENTS_NOT_FOUND_MSG));
    }

    @RequestMapping(value = "/{jobId}/_buildings", method = RequestMethod.GET)
    public ResponseEntity<Resource> targetsV1BuildingsGet(@PathVariable String jobId) {
        return jsonArtifact(targetService.getArtifact(jobId, BUILDINGS_EXTENSION, BUILDINGS_NOT_FOUND_MSG));
    }

    @RequestMapping(value = "/{jobId}/_stats", method = RequestMethod.GET)
    public ResponseEntity<Resource> targetsV1StatsGet(@PathVariable String jobId) {
        return jsonArtifact(targetService.getArtifact(jobId, STATS_EXTENSION, STATS_NOT_FOUND_MSG));
    }

    private ResponseEntity<Resource> jsonArtifact(Path artifact) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PathResource(artifact));
    }
}
