package org.pdi.web;

import org.pdi.engine.ComputeCommand;
import org.pdi.engine.EngineInput;
import org.pdi.service.ArtifactStore;
import org.pdi.service.Job;
import org.pdi.service.TargetService;
import org.pdi.service.Uploads;
import org.pdi.web.dto.JobStatusResponse;
import org.pdi.web.dto.JobSubmitResponse;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

@RestController
@RequestMapping("/population/v1/targets")
public class TargetController {

    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final TargetService targetService;
    private final ArtifactStore artifacts;

    public TargetController(TargetService targetService, ArtifactStore artifacts) {
        this.targetService = targetService;
        this.artifacts = artifacts;
    }

    /**
     * Start a compute run.
     *
     * <p>{@code boundaries} is the catchment geojson and {@code enumeration} the field
     * workbook; both are optional, and each one supplied adds a layer to the result. The
     * older {@code sheet} parameter still works but is superseded by {@code boundaries}.
     */
    @PostMapping(path = "/_compute", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public JobSubmitResponse compute(@RequestParam(value = "boundaries", required = false) MultipartFile boundaries,
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
        files.forEach(TargetController::requireExpectedExtension);

        String normalizedIso3 = normalizeIso3(iso3);
        String resolvedCampaign = resolveCampaignId(campaignId, normalizedIso3, year);
        ComputeCommand command = new ComputeCommand(
                normalizedIso3, year, householdSize, groups, withBuildings, resolvedCampaign, tenantId, force);
        String jobId = targetService.submit(files, command);
        return new JobSubmitResponse(jobId, "/population/v1/targets/" + jobId + "/_status");
    }

    private static void requireExpectedExtension(EngineInput input, MultipartFile file) {
        if (!Uploads.hasExpectedExtension(input, file)) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    input.parameter() + " must be a " + input.extension() + " file, got "
                            + file.getOriginalFilename());
        }
    }

    private String resolveCampaignId(String campaignId, String iso3, Integer year) {
        if (campaignId != null && !campaignId.isBlank()) {
            return campaignId.trim();
        }
        return "PDI-" + iso3 + "-" + (year != null ? year : "DEFAULT");
    }

    @GetMapping("/{jobId}/_status")
    public JobStatusResponse status(@PathVariable String jobId) {
        Job job = targetService.job(jobId);
        if (job == null) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "job not found");
        }
        TargetService.Progress progress = targetService.progress(job);
        return new JobStatusResponse(
                jobId,
                job.status().name(),
                progress.message(),
                progress.percent(),
                job.status() == Job.Status.DONE ? job.result() : null,
                job.status() == Job.Status.FAILED ? job.error() : null);
    }

    @GetMapping("/{jobId}/_download")
    public ResponseEntity<Resource> download(@PathVariable String jobId) {
        Path sheet = artifacts.find(jobId, "xlsx")
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "target sheet not found"));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"targets-" + jobId + ".xlsx\"")
                .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
                .body(new PathResource(sheet));
    }

    @GetMapping("/{jobId}/_geojson")
    public ResponseEntity<Resource> geojson(@PathVariable String jobId) {
        Path geojson = artifacts.find(jobId, "geojson")
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "boundary geojson not found"));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PathResource(geojson));
    }

    @GetMapping("/{jobId}/_settlements")
    public ResponseEntity<Resource> settlements(@PathVariable String jobId) {
        Path settlements = artifacts.find(jobId, "settlements.geojson")
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "invisible settlements not found"));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PathResource(settlements));
    }

    @GetMapping("/{jobId}/_catchments")
    public ResponseEntity<Resource> catchments(@PathVariable String jobId) {
        Path catchments = artifacts.find(jobId, "catchments.geojson")
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "catchment cells not found"));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PathResource(catchments));
    }

    @GetMapping("/{jobId}/_buildings")
    public ResponseEntity<Resource> buildings(@PathVariable String jobId) {
        Path buildings = artifacts.find(jobId, "buildings.geojson")
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "catchment buildings not found"));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PathResource(buildings));
    }

    @GetMapping("/{jobId}/_stats")
    public ResponseEntity<Resource> stats(@PathVariable String jobId) {
        Path stats = artifacts.find(jobId, "stats.json")
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "dashboard stats not found"));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PathResource(stats));
    }

    private String normalizeIso3(String iso3) {
        if (iso3 == null || iso3.isBlank()) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "iso3 is required");
        }
        return iso3.trim().toUpperCase();
    }
}
