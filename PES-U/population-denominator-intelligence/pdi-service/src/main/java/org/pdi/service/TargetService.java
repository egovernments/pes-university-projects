package org.pdi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.pdi.exception.CustomException;
import org.pdi.repository.ArtifactRepository;
import org.pdi.repository.ResultCacheRepository;
import org.pdi.service.enrichment.TargetEnrichmentService;
import org.pdi.util.PythonEngineUtil;
import org.pdi.util.Uploads;
import org.pdi.validator.target.TargetValidator;
import org.pdi.web.models.CachedResult;
import org.pdi.web.models.ComputeCommand;
import org.pdi.web.models.EngineInput;
import org.pdi.web.models.EngineResult;
import org.pdi.web.models.Job;
import org.pdi.web.models.JobProgress;
import org.pdi.web.models.JobStatusResponse;
import org.pdi.web.models.TargetComputeResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.pdi.util.PdiConstants.ARTIFACT_NOT_FOUND;
import static org.pdi.util.PdiConstants.BUILDINGS_EXTENSION;
import static org.pdi.util.PdiConstants.CATCHMENTS_EXTENSION;
import static org.pdi.util.PdiConstants.DEFAULT_TENANT_ID;
import static org.pdi.util.PdiConstants.DOWNLOADING_MESSAGE;
import static org.pdi.util.PdiConstants.ENGINE_ERROR;
import static org.pdi.util.PdiConstants.ENGINE_LOG_FILE;
import static org.pdi.util.PdiConstants.GEOJSON_EXTENSION;
import static org.pdi.util.PdiConstants.JOB_NOT_FOUND;
import static org.pdi.util.PdiConstants.JOB_NOT_FOUND_MSG;
import static org.pdi.util.PdiConstants.PROGRESS_PREFIX;
import static org.pdi.util.PdiConstants.SETTLEMENTS_EXTENSION;
import static org.pdi.util.PdiConstants.STATS_EXTENSION;
import static org.pdi.util.PdiConstants.XLSX_EXTENSION;
import static org.pdi.util.PdiServiceUtil.deleteDirQuietly;
import static org.pdi.util.PdiServiceUtil.lastLine;
import static org.pdi.util.PdiServiceUtil.readOrEmpty;
import static org.pdi.util.PdiServiceUtil.readableFailure;
import static org.pdi.util.PdiServiceUtil.sha256Hex;
import static org.pdi.util.PdiServiceUtil.writeBytes;
import static org.pdi.util.PdiServiceUtil.writeText;

@Slf4j
@Service
public class TargetService {

    private final PythonEngineUtil pythonEngineUtil;

    private final ArtifactRepository artifactRepository;

    private final ResultCacheRepository resultCacheRepository;

    private final TargetEnrichmentService targetEnrichmentService;

    private final TargetValidator targetValidator;

    private final ObjectMapper objectMapper;

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "pdi-engine");
        thread.setDaemon(true);
        return thread;
    });

    @Autowired
    public TargetService(PythonEngineUtil pythonEngineUtil, ArtifactRepository artifactRepository,
                         ResultCacheRepository resultCacheRepository,
                         TargetEnrichmentService targetEnrichmentService, TargetValidator targetValidator,
                         ObjectMapper objectMapper) {
        this.pythonEngineUtil = pythonEngineUtil;
        this.artifactRepository = artifactRepository;
        this.resultCacheRepository = resultCacheRepository;
        this.targetEnrichmentService = targetEnrichmentService;
        this.targetValidator = targetValidator;
        this.objectMapper = objectMapper;
    }

    public String submit(Map<EngineInput, MultipartFile> files, ComputeCommand command) {
        targetValidator.validateComputeRequest(files, command.getIso3());
        targetEnrichmentService.enrichComputeRequest(command);

        Uploads uploads = Uploads.stage(files);
        String cacheKey = cacheKey(command, uploads);
        String paramsJson = paramsJson(command, uploads);

        String jobId = UUID.randomUUID().toString();
        Path work = createWorkDir(jobId);
        Path logFile = work.resolve(ENGINE_LOG_FILE);
        writeInitialLog(logFile, command.getIso3(), uploads);

        Job job = new Job(jobId, logFile);
        jobs.put(jobId, job);
        executor.submit(() -> runJob(job, uploads, command, cacheKey, paramsJson));
        log.info("Submitted compute job {} for {}", jobId, command.getIso3());
        return jobId;
    }

    public JobStatusResponse getJobStatus(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) {
            throw new CustomException(JOB_NOT_FOUND, JOB_NOT_FOUND_MSG, HttpStatus.NOT_FOUND);
        }
        JobProgress progress = parseProgress(lastLine(readOrEmpty(job.getLogFile())));
        return JobStatusResponse.builder()
                .jobId(jobId)
                .status(job.getStatus().name())
                .message(progress.getMessage())
                .percent(progress.getPercent())
                .result(job.getStatus() == Job.Status.DONE ? job.getResult() : null)
                .error(job.getStatus() == Job.Status.FAILED ? job.getError() : null)
                .build();
    }

    public Path getArtifact(String jobId, String extension, String notFoundMessage) {
        return artifactRepository.find(jobId, extension)
                .orElseThrow(() -> new CustomException(ARTIFACT_NOT_FOUND, notFoundMessage, HttpStatus.NOT_FOUND));
    }

    private JobProgress parseProgress(String line) {
        if (line != null && line.startsWith(PROGRESS_PREFIX)) {
            String rest = line.substring(PROGRESS_PREFIX.length()).strip();
            int space = rest.indexOf(' ');
            String head = space < 0 ? rest : rest.substring(0, space);
            try {
                int pct = Math.max(0, Math.min(100, Integer.parseInt(head)));
                String text = space < 0 ? DOWNLOADING_MESSAGE : rest.substring(space + 1).strip();
                return new JobProgress(text, pct);
            } catch (NumberFormatException ignored) {
                // fall through to the plain-text case
            }
        }
        return new JobProgress(line, null);
    }

    private void runJob(Job job, Uploads uploads, ComputeCommand command, String cacheKey, String paramsJson) {
        try {
            // A forced recompute skips the lookup but still writes its result back, so
            // the stored entry is refreshed rather than duplicated or left stale.
            if (!command.isForce()) {
                Optional<CachedResult> cached = resultCacheRepository.find(cacheKey);
                if (cached.isPresent()) {
                    job.complete(serveFromCache(job, cached.get()));
                    return;
                }
            } else {
                appendLog(job, "recompute requested - ignoring cached result");
            }

            EngineResult result = pythonEngineUtil.compute(uploads, command, job.getLogFile());
            Path filledSheet = result.getSheet() == null ? null : Path.of(result.getSheet());
            Path geojson = result.getGeojson() == null ? null : Path.of(result.getGeojson());
            Path settlements = result.getInvisibleGeojson() == null ? null : Path.of(result.getInvisibleGeojson());
            Path catchments = result.getCatchmentsGeojson() == null ? null : Path.of(result.getCatchmentsGeojson());
            Path buildings = result.getBuildingsGeojson() == null ? null : Path.of(result.getBuildingsGeojson());
            Path stats = result.getStatsJson() == null ? null : Path.of(result.getStatsJson());
            String artifactId = artifactRepository.store(filledSheet, geojson, settlements, catchments, buildings, stats);
            TargetComputeResponse response = targetEnrichmentService.enrichComputeResponse(
                    artifactId, result,
                    filledSheet != null, geojson != null, settlements != null,
                    catchments != null, buildings != null, stats != null);
            job.complete(response);
            saveToCache(cacheKey, result.getIso3(), paramsJson, artifactId, response);
        } catch (RuntimeException e) {
            log.error("Compute job {} failed: {}", job.getId(), e.getMessage());
            job.fail(readableFailure(lastLine(e.getMessage())));
        } finally {
            uploads.deleteAll();
        }
    }

    private void saveToCache(String cacheKey, String iso3, String paramsJson,
                             String artifactId, TargetComputeResponse response) {
        try {
            String responseJson = objectMapper.writeValueAsString(response);
            resultCacheRepository.save(cacheKey, iso3, paramsJson, responseJson,
                    readText(artifactId, GEOJSON_EXTENSION),
                    readText(artifactId, STATS_EXTENSION),
                    readText(artifactId, SETTLEMENTS_EXTENSION),
                    readText(artifactId, CATCHMENTS_EXTENSION),
                    readText(artifactId, BUILDINGS_EXTENSION),
                    readBytes(artifactId, XLSX_EXTENSION));
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Could not cache result for {}: {}", iso3, e.getMessage());
        }
    }

    /** Rehydrate a cached run: write its artifacts back to disk and re-mint the response URLs. */
    private TargetComputeResponse serveFromCache(Job job, CachedResult cached) {
        Path dir = createTempDir();
        try {
            Path sheet = writeBytes(dir, "targets.xlsx", cached.getSheetXlsx());
            Path geojson = writeText(dir, "units.geojson", cached.getGeojson());
            Path settlements = writeText(dir, "settlements.geojson", cached.getSettlements());
            Path catchments = writeText(dir, "catchments.geojson", cached.getCatchments());
            Path buildings = writeText(dir, "buildings.geojson", cached.getBuildings());
            Path stats = writeText(dir, "stats.json", cached.getStatsJson());
            String artifactId = artifactRepository.store(sheet, geojson, settlements, catchments, buildings, stats);

            appendLog(job, "served from cache");
            TargetComputeResponse stored = objectMapper.readValue(cached.getResponse(), TargetComputeResponse.class);
            return targetEnrichmentService.enrichCachedResponse(artifactId, stored,
                    cached.getSheetXlsx() != null, cached.getGeojson() != null, cached.getSettlements() != null,
                    cached.getCatchments() != null, cached.getBuildings() != null, cached.getStatsJson() != null);
        } catch (IOException e) {
            throw new CustomException(ENGINE_ERROR, "could not rehydrate cached result",
                    HttpStatus.UNPROCESSABLE_ENTITY, e);
        } finally {
            deleteDirQuietly(dir);
        }
    }

    /**
     * Identity of a run: its parameters plus every uploaded file's hash. A changed
     * enumeration sheet against unchanged boundaries yields a different key, so the
     * corrected numbers recompute instead of being served from the previous run.
     */
    private String cacheKey(ComputeCommand command, Uploads uploads) {
        String raw = String.join("|",
                command.getIso3(),
                command.getYear() == null ? "default" : command.getYear().toString(),
                command.getHouseholdSize() == null ? "default" : command.getHouseholdSize().toString(),
                Boolean.toString(command.isWithBuildings()),
                command.getGroups() == null ? "" : command.getGroups(),
                command.getCampaignId() == null ? "" : command.getCampaignId(),
                command.getTenantId() == null ? DEFAULT_TENANT_ID : command.getTenantId(),
                uploads.fingerprint());
        return sha256Hex(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String paramsJson(ComputeCommand command, Uploads uploads) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("year", command.getYear());
        params.put("householdSize", command.getHouseholdSize());
        params.put("withBuildings", command.isWithBuildings());
        params.put("groups", command.getGroups());
        params.put("campaignId", command.getCampaignId());
        params.put("tenantId", command.getTenantId());
        params.put("uploads", uploads.hashes());
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String readText(String artifactId, String extension) {
        return artifactRepository.find(artifactId, extension)
                .map(path -> {
                    try {
                        return Files.readString(path, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    private byte[] readBytes(String artifactId, String extension) {
        return artifactRepository.find(artifactId, extension)
                .map(path -> {
                    try {
                        return Files.readAllBytes(path);
                    } catch (IOException e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    private Path createWorkDir(String jobId) {
        try {
            return Files.createTempDirectory("pdi-job-" + jobId + "-");
        } catch (IOException e) {
            throw new CustomException(ENGINE_ERROR, "could not create job work directory",
                    HttpStatus.UNPROCESSABLE_ENTITY, e);
        }
    }

    private Path createTempDir() {
        try {
            return Files.createTempDirectory("pdi-cache-");
        } catch (IOException e) {
            throw new CustomException(ENGINE_ERROR, "could not create cache staging directory",
                    HttpStatus.UNPROCESSABLE_ENTITY, e);
        }
    }

    private void writeInitialLog(Path logFile, String iso3, Uploads uploads) {
        String inputs = uploads.isEmpty() ? "no uploads" : String.join(", ", uploads.hashes().keySet());
        try {
            Files.writeString(logFile, "preparing " + iso3 + " (" + inputs + ")" + System.lineSeparator());
        } catch (IOException ignored) {
            // The log is progress reporting only; failing to seed it must not fail the job.
        }
    }

    private void appendLog(Job job, String line) {
        try {
            Files.writeString(job.getLogFile(), line + System.lineSeparator(),
                    StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }
}
