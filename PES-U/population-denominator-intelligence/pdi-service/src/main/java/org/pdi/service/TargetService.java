package org.pdi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.pdi.engine.ComputeCommand;
import org.pdi.engine.EngineException;
import org.pdi.engine.EngineInput;
import org.pdi.engine.EngineResult;
import org.pdi.engine.PythonEngineRunner;
import org.pdi.web.dto.BoundaryTarget;
import org.pdi.web.dto.TargetComputeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class TargetService {

    private static final Logger log = LoggerFactory.getLogger(TargetService.class);
    private static final String CODE_KEY = "boundaryCode";

    private final PythonEngineRunner engine;
    private final ArtifactStore artifacts;
    private final ResultCacheStore cache;
    private final ObjectMapper objectMapper;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "pdi-engine");
        thread.setDaemon(true);
        return thread;
    });

    public TargetService(PythonEngineRunner engine, ArtifactStore artifacts,
                         ResultCacheStore cache, ObjectMapper objectMapper) {
        this.engine = engine;
        this.artifacts = artifacts;
        this.cache = cache;
        this.objectMapper = objectMapper;
    }

    public String submit(Map<EngineInput, MultipartFile> files, ComputeCommand command) {
        Uploads uploads = Uploads.stage(files);
        String cacheKey = cacheKey(command, uploads);
        String paramsJson = paramsJson(command, uploads);

        String jobId = UUID.randomUUID().toString();
        Path work = createWorkDir(jobId);
        Path logFile = work.resolve("engine.log");
        writeInitialLog(logFile, command.iso3(), uploads);

        Job job = new Job(jobId, logFile);
        jobs.put(jobId, job);
        executor.submit(() -> runJob(job, uploads, command, cacheKey, paramsJson));
        return jobId;
    }

    public Job job(String jobId) {
        return jobs.get(jobId);
    }

    /** Latest human-readable step plus, when a download is streaming, its percentage. */
    public record Progress(String message, Integer percent) {
    }

    public Progress progress(Job job) {
        return parseProgress(lastLine(readOrEmpty(job.logFile())));
    }

    private Progress parseProgress(String line) {
        if (line != null && line.startsWith("PROGRESS ")) {
            String rest = line.substring("PROGRESS ".length()).strip();
            int space = rest.indexOf(' ');
            String head = space < 0 ? rest : rest.substring(0, space);
            try {
                int pct = Math.max(0, Math.min(100, Integer.parseInt(head)));
                String text = space < 0 ? "downloading" : rest.substring(space + 1).strip();
                return new Progress(text, pct);
            } catch (NumberFormatException ignored) {
                // fall through to the plain-text case
            }
        }
        return new Progress(line, null);
    }

    private void runJob(Job job, Uploads uploads, ComputeCommand command, String cacheKey, String paramsJson) {
        try {
            // A forced recompute skips the lookup but still writes its result back, so
            // the stored entry is refreshed rather than duplicated or left stale.
            if (!command.force()) {
                Optional<CachedResult> cached = cache.find(cacheKey);
                if (cached.isPresent()) {
                    job.complete(serveFromCache(job, cached.get()));
                    return;
                }
            } else {
                appendLog(job, "recompute requested - ignoring cached result");
            }

            EngineResult result = engine.compute(uploads, command, job.logFile());
            Path filledSheet = result.sheet() == null ? null : Path.of(result.sheet());
            Path geojson = result.geojson() == null ? null : Path.of(result.geojson());
            Path settlements = result.invisibleGeojson() == null ? null : Path.of(result.invisibleGeojson());
            Path catchments = result.catchmentsGeojson() == null ? null : Path.of(result.catchmentsGeojson());
            Path buildings = result.buildingsGeojson() == null ? null : Path.of(result.buildingsGeojson());
            Path stats = result.statsJson() == null ? null : Path.of(result.statsJson());
            String artifactId = artifacts.store(filledSheet, geojson, settlements, catchments, buildings, stats);
            TargetComputeResponse response = buildResponse(
                    artifactId, result,
                    filledSheet != null, geojson != null, settlements != null,
                    catchments != null, buildings != null, stats != null);
            job.complete(response);
            saveToCache(cacheKey, result.iso3(), paramsJson, artifactId, response);
        } catch (RuntimeException e) {
            job.fail(readableFailure(lastLine(e.getMessage())));
        } finally {
            uploads.deleteAll();
        }
    }

    private TargetComputeResponse buildResponse(String artifactId, EngineResult result,
                                                boolean sheet, boolean geojson, boolean settlements,
                                                boolean catchments, boolean buildings, boolean stats) {
        String base = "/population/v1/targets/" + artifactId;
        return new TargetComputeResponse(
                artifactId,
                result.iso3(),
                result.count(),
                result.groups(),
                toTargets(result.targets()),
                result.registeredAvailable(),
                toTargets(result.registered()),
                result.invisibleAvailable(),
                result.invisibleCount(),
                result.catchmentsAvailable(),
                result.catchmentCount(),
                result.riskAvailable(),
                sheet ? base + "/_download" : null,
                geojson ? base + "/_geojson" : null,
                settlements ? base + "/_settlements" : null,
                catchments ? base + "/_catchments" : null,
                buildings ? base + "/_buildings" : null,
                stats ? base + "/_stats" : null,
                result.provenance());
    }

    // --- Result cache -------------------------------------------------------

    private void saveToCache(String cacheKey, String iso3, String paramsJson,
                             String artifactId, TargetComputeResponse response) {
        try {
            String responseJson = objectMapper.writeValueAsString(response);
            cache.save(cacheKey, iso3, paramsJson, responseJson,
                    readText(artifactId, "geojson"),
                    readText(artifactId, "stats.json"),
                    readText(artifactId, "settlements.geojson"),
                    readText(artifactId, "catchments.geojson"),
                    readText(artifactId, "buildings.geojson"),
                    readBytes(artifactId, "xlsx"));
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Could not cache result for {}: {}", iso3, e.getMessage());
        }
    }

    /** Rehydrate a cached run: write its artifacts back to disk and re-mint the response URLs. */
    private TargetComputeResponse serveFromCache(Job job, CachedResult cached) {
        Path dir = createTempDir();
        try {
            Path sheet = writeBytes(dir, "targets.xlsx", cached.sheetXlsx());
            Path geojson = writeText(dir, "units.geojson", cached.geojson());
            Path settlements = writeText(dir, "settlements.geojson", cached.settlements());
            Path catchments = writeText(dir, "catchments.geojson", cached.catchments());
            Path buildings = writeText(dir, "buildings.geojson", cached.buildings());
            Path stats = writeText(dir, "stats.json", cached.statsJson());
            String artifactId = artifacts.store(sheet, geojson, settlements, catchments, buildings, stats);

            appendLog(job, "served from cache");
            TargetComputeResponse stored = objectMapper.readValue(cached.response(), TargetComputeResponse.class);
            String base = "/population/v1/targets/" + artifactId;
            return new TargetComputeResponse(
                    artifactId,
                    stored.iso3(),
                    stored.boundaryCount(),
                    stored.groups(),
                    stored.targets(),
                    stored.registeredAvailable(),
                    stored.registered(),
                    stored.invisibleAvailable(),
                    stored.invisibleCount(),
                    stored.catchmentsAvailable(),
                    stored.catchmentCount(),
                    stored.riskAvailable(),
                    cached.sheetXlsx() != null ? base + "/_download" : null,
                    cached.geojson() != null ? base + "/_geojson" : null,
                    cached.settlements() != null ? base + "/_settlements" : null,
                    cached.catchments() != null ? base + "/_catchments" : null,
                    cached.buildings() != null ? base + "/_buildings" : null,
                    cached.statsJson() != null ? base + "/_stats" : null,
                    stored.provenance());
        } catch (IOException e) {
            throw new EngineException("could not rehydrate cached result", e);
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
                command.iso3(),
                command.year() == null ? "default" : command.year().toString(),
                command.householdSize() == null ? "default" : command.householdSize().toString(),
                Boolean.toString(command.withBuildings()),
                command.groups() == null ? "" : command.groups(),
                command.campaignId() == null ? "" : command.campaignId(),
                command.tenantId() == null ? "default" : command.tenantId(),
                uploads.fingerprint());
        return sha256Hex(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String paramsJson(ComputeCommand command, Uploads uploads) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("year", command.year());
        params.put("householdSize", command.householdSize());
        params.put("withBuildings", command.withBuildings());
        params.put("groups", command.groups());
        params.put("campaignId", command.campaignId());
        params.put("tenantId", command.tenantId());
        params.put("uploads", uploads.hashes());
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String readText(String artifactId, String extension) {
        return artifacts.find(artifactId, extension)
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
        return artifacts.find(artifactId, extension)
                .map(path -> {
                    try {
                        return Files.readAllBytes(path);
                    } catch (IOException e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    private Path writeText(Path dir, String name, String content) {
        if (content == null) {
            return null;
        }
        try {
            Path file = dir.resolve(name);
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("could not stage cached artifact " + name, e);
        }
    }

    private Path writeBytes(Path dir, String name, byte[] content) {
        if (content == null) {
            return null;
        }
        try {
            Path file = dir.resolve(name);
            Files.write(file, content);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("could not stage cached artifact " + name, e);
        }
    }

    // --- Helpers ------------------------------------------------------------

    private List<BoundaryTarget> toTargets(List<Map<String, Object>> records) {
        return records.stream().map(this::toTarget).toList();
    }

    private BoundaryTarget toTarget(Map<String, Object> record) {
        Map<String, Long> values = new LinkedHashMap<>();
        String boundaryCode = null;
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            if (CODE_KEY.equals(entry.getKey())) {
                boundaryCode = String.valueOf(entry.getValue());
            } else if (entry.getValue() instanceof Number number) {
                values.put(entry.getKey(), number.longValue());
            }
        }
        return new BoundaryTarget(boundaryCode, values);
    }

    private Path createWorkDir(String jobId) {
        try {
            return Files.createTempDirectory("pdi-job-" + jobId + "-");
        } catch (IOException e) {
            throw new EngineException("could not create job work directory", e);
        }
    }

    private Path createTempDir() {
        try {
            return Files.createTempDirectory("pdi-cache-");
        } catch (IOException e) {
            throw new EngineException("could not create cache staging directory", e);
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
            Files.writeString(job.logFile(), line + System.lineSeparator(),
                    StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    private String readOrEmpty(Path log) {
        try {
            return Files.readString(log, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    // The engine raises ordinary Python exceptions for bad uploads, so the last line of a
    // failed run reads "ValueError: this boundary file is not in TCD ...". The class name
    // is noise to whoever uploaded the file; the sentence after it is the actual message.
    private static final java.util.regex.Pattern PYTHON_EXCEPTION =
            java.util.regex.Pattern.compile("^[A-Za-z_][A-Za-z0-9_.]*(Error|Exception):\\s+");

    private String readableFailure(String line) {
        if (line == null || line.isBlank()) {
            return "The engine failed without reporting a reason. Check the API logs.";
        }
        return PYTHON_EXCEPTION.matcher(line).replaceFirst("");
    }

    private String lastLine(String text) {
        if (text == null || text.isBlank()) {
            return "working";
        }
        String[] lines = text.strip().split("\\R");
        return lines[lines.length - 1].strip();
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private void deleteDirQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(this::deleteQuietly);
        } catch (IOException ignored) {
        }
    }
}
