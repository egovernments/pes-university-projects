package org.pdi.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.pdi.config.PdiConfiguration;
import org.pdi.exception.CustomException;
import org.pdi.web.models.BoundaryPopulationResult;
import org.pdi.web.models.ComputeCommand;
import org.pdi.web.models.EngineInput;
import org.pdi.web.models.EngineResult;
import org.pdi.web.models.GeometryParseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

import static org.pdi.util.PdiConstants.DEFAULT_TENANT_ID;
import static org.pdi.util.PdiConstants.ENGINE_ERROR;
import static org.pdi.util.PdiConstants.LOG_TAIL_CHARS;
import static org.pdi.util.PdiServiceUtil.lastLine;
import static org.pdi.util.PdiServiceUtil.progressPercent;
import static org.pdi.util.PdiServiceUtil.readOrEmpty;
import static org.pdi.util.PdiServiceUtil.readableFailure;

/**
 * Runs the Python batch engine as a subprocess ({@code python -m features.<command>}) and
 * reads back the result document it writes.
 */
@Slf4j
@Component
public class PythonEngineUtil {

    private static final long PROGRESS_POLL_SECONDS = 2;

    private final PdiConfiguration config;

    private final ObjectMapper objectMapper;

    @Autowired
    public PythonEngineUtil(PdiConfiguration config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

    public EngineResult compute(Uploads uploads, ComputeCommand command, Path logFile) {
        Path work = logFile.getParent();
        Path outSheet = work.resolve("targets.xlsx");
        Path geojson = work.resolve("units.geojson");
        Path settlements = work.resolve("settlements.geojson");
        Path catchments = work.resolve("catchments.geojson");
        Path buildings = work.resolve("buildings.geojson");
        Path stats = work.resolve("stats.json");
        Path resultJson = work.resolve("result.json");

        Process process = start(
                buildCommand(uploads, outSheet, geojson, settlements, catchments, buildings,
                        stats, resultJson, command),
                logFile);
        awaitCompletion(process, logFile, null);
        return readResult(resultJson, EngineResult.class);
    }

    /** WorldPop population per boundary code over the resolved units ({@code features.boundary_population}). */
    public BoundaryPopulationResult boundaryPopulation(Path units, Path clips, String iso3, Integer year,
                                                       List<String> groups, Path logFile, IntConsumer progress) {
        Path resultJson = logFile.getParent().resolve("boundary-population.json");
        List<String> args = new ArrayList<>(List.of(
                config.getEnginePython(), "-u", "-m", "features.boundary_population",
                "--units", units.toString(),
                "--iso3", iso3,
                "--groups", String.join(",", groups),
                "--out", resultJson.toString()));
        if (clips != null) {
            args.add("--clips");
            args.add(clips.toString());
        }
        if (year != null) {
            args.add("--year");
            args.add(year.toString());
        }
        awaitCompletion(start(args, logFile), logFile, progress);
        return readResult(resultJson, BoundaryPopulationResult.class);
    }

    /** Writes the fill-in geometry sheet for the given boundary rows. */
    public void generateGeometrySheet(Path rowsJson, Path outSheet, Path logFile) {
        List<String> args = List.of(config.getEnginePython(), "-u", "-m", "features.boundary_geometry", "generate",
                "--rows", rowsJson.toString(), "--out", outSheet.toString());
        awaitCompletion(start(args, logFile), logFile, null);
    }

    /** Checks an uploaded geometry sheet and shape file against the hierarchy. */
    public GeometryParseResult parseGeometryUpload(Path treeJson, String iso3, Path sheet, Path shapes,
                                                   Path storedPolygons, Path annotatedSheet, Path logFile) {
        Path resultJson = logFile.getParent().resolve("geometry-upload.json");
        List<String> args = new ArrayList<>(List.of(
                config.getEnginePython(), "-u", "-m", "features.boundary_geometry", "parse",
                "--tree", treeJson.toString(), "--iso3", iso3, "--out", resultJson.toString()));
        addOptional(args, "--sheet", sheet);
        addOptional(args, "--shapes", shapes);
        addOptional(args, "--stored", storedPolygons);
        if (sheet != null) {
            addOptional(args, "--annotated", annotatedSheet);
        }
        awaitCompletion(start(args, logFile), logFile, null);
        return readResult(resultJson, GeometryParseResult.class);
    }

    private void addOptional(List<String> args, String flag, Path value) {
        if (value != null) {
            args.add(flag);
            args.add(value.toAbsolutePath().toString());
        }
    }

    private List<String> buildCommand(Uploads uploads, Path outSheet, Path geojson, Path settlements,
                                      Path catchments, Path buildings, Path stats, Path resultJson,
                                      ComputeCommand command) {
        List<String> args = new ArrayList<>(List.of(
                config.getEnginePython(), "-u", "-m", "features.targets",
                "--iso3", command.getIso3(),
                "--geojson", geojson.toString(),
                "--invisible-geojson", settlements.toString(),
                "--catchments-geojson", catchments.toString(),
                "--buildings-geojson", buildings.toString(),
                "--stats-json", stats.toString(),
                "--json", resultJson.toString()));

        boolean hasUnits = false;
        for (EngineInput input : EngineInput.values()) {
            Path staged = uploads.path(input);
            if (staged == null) {
                continue;
            }
            args.add(input.getEngineFlag());
            args.add(staged.toAbsolutePath().toString());
            hasUnits |= input == EngineInput.BOUNDARIES || input == EngineInput.SHEET;
        }
        // The workbook is only produced when there are catchment units to describe.
        if (hasUnits) {
            args.add("--out");
            args.add(outSheet.toString());
        }
        if (command.getYear() != null) {
            args.add("--year");
            args.add(command.getYear().toString());
        }
        if (command.getHouseholdSize() != null) {
            args.add("--household-size");
            args.add(command.getHouseholdSize().toString());
        }
        if (command.getGroups() != null && !command.getGroups().isBlank()) {
            args.add("--groups");
            args.add(command.getGroups());
        }
        if (!command.isWithBuildings()) {
            args.add("--no-buildings");
        }
        if (command.getCampaignId() != null && !command.getCampaignId().isBlank()) {
            args.add("--persist");
            args.add("--campaign-id");
            args.add(command.getCampaignId());
            args.add("--tenant-id");
            args.add(command.getTenantId() == null ? DEFAULT_TENANT_ID : command.getTenantId());
            if (config.getPersistBuildings()) {
                args.add("--persist-buildings");
            }
        }
        return args;
    }

    private Process start(List<String> args, Path logFile) {
        ProcessBuilder builder = new ProcessBuilder(args)
                .directory(new File(config.getEngineWorkdir()))
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile());
        try {
            return builder.start();
        } catch (IOException e) {
            throw new CustomException(ENGINE_ERROR, "could not start the PDI engine process",
                    HttpStatus.UNPROCESSABLE_ENTITY, e);
        }
    }

    /**
     * Waits for the engine, reporting the percent of its latest {@code PROGRESS} line to
     * {@code progress} (when given) while it runs.
     */
    private void awaitCompletion(Process process, Path logFile, IntConsumer progress) {
        try {
            long timeout = config.getEngineTimeoutSeconds();
            // No cap when timeout <= 0: the first run for a country downloads WorldPop rasters
            // (and can take hours on a slow link). The job is async and streams progress to the
            // log, so the batch is let run to completion.
            long deadline = timeout <= 0 ? Long.MAX_VALUE : System.nanoTime() + TimeUnit.SECONDS.toNanos(timeout);
            while (!process.waitFor(PROGRESS_POLL_SECONDS, TimeUnit.SECONDS)) {
                if (System.nanoTime() > deadline) {
                    process.destroyForcibly();
                    throw new CustomException(ENGINE_ERROR, "engine timed out after " + timeout + "s",
                            HttpStatus.UNPROCESSABLE_ENTITY);
                }
                if (progress != null) {
                    Integer percent = progressPercent(lastLine(readOrEmpty(logFile)));
                    if (percent != null) {
                        progress.accept(percent);
                    }
                }
            }
            if (process.exitValue() != 0) {
                String output = tail(logFile);
                log.error("Engine exited with {}: {}", process.exitValue(), output);
                throw new CustomException(ENGINE_ERROR, readableFailure(lastLine(output)),
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CustomException(ENGINE_ERROR, "engine run was interrupted",
                    HttpStatus.UNPROCESSABLE_ENTITY, e);
        }
    }

    private <T> T readResult(Path resultJson, Class<T> type) {
        try {
            return objectMapper.readValue(resultJson.toFile(), type);
        } catch (IOException e) {
            throw new CustomException(ENGINE_ERROR, "could not read engine result",
                    HttpStatus.UNPROCESSABLE_ENTITY, e);
        }
    }

    private String tail(Path logFile) {
        try {
            String content = Files.readString(logFile, StandardCharsets.UTF_8);
            return content.length() <= LOG_TAIL_CHARS
                    ? content
                    : content.substring(content.length() - LOG_TAIL_CHARS);
        } catch (IOException e) {
            return "(no engine output captured)";
        }
    }
}
