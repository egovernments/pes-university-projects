package org.pdi.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.pdi.config.PdiConfiguration;
import org.pdi.exception.CustomException;
import org.pdi.web.models.ComputeCommand;
import org.pdi.web.models.EngineInput;
import org.pdi.web.models.EngineResult;
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

import static org.pdi.util.PdiConstants.DEFAULT_TENANT_ID;
import static org.pdi.util.PdiConstants.ENGINE_ERROR;
import static org.pdi.util.PdiConstants.LOG_TAIL_CHARS;

/**
 * Runs the Python batch engine ({@code python -m features.targets}) as a subprocess and
 * reads back the result document it writes.
 */
@Component
public class PythonEngineUtil {

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
        awaitCompletion(process, logFile);
        return readResult(resultJson);
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

    private Process start(List<String> args, Path log) {
        ProcessBuilder builder = new ProcessBuilder(args)
                .directory(new File(config.getEngineWorkdir()))
                .redirectErrorStream(true)
                .redirectOutput(log.toFile());
        try {
            return builder.start();
        } catch (IOException e) {
            throw new CustomException(ENGINE_ERROR, "could not start the PDI engine process",
                    HttpStatus.UNPROCESSABLE_ENTITY, e);
        }
    }

    private void awaitCompletion(Process process, Path log) {
        try {
            long timeout = config.getEngineTimeoutSeconds();
            if (timeout <= 0) {
                // No cap: the first run for a country downloads WorldPop rasters
                // (and can take hours on a slow link). The job is async and streams
                // progress to the log, so we let the batch run to completion.
                process.waitFor();
            } else {
                boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    throw new CustomException(ENGINE_ERROR, "engine timed out after " + timeout + "s",
                            HttpStatus.UNPROCESSABLE_ENTITY);
                }
            }
            if (process.exitValue() != 0) {
                throw new CustomException(ENGINE_ERROR, "engine failed: " + tail(log),
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CustomException(ENGINE_ERROR, "engine run was interrupted",
                    HttpStatus.UNPROCESSABLE_ENTITY, e);
        }
    }

    private EngineResult readResult(Path resultJson) {
        try {
            return objectMapper.readValue(resultJson.toFile(), EngineResult.class);
        } catch (IOException e) {
            throw new CustomException(ENGINE_ERROR, "could not read engine result",
                    HttpStatus.UNPROCESSABLE_ENTITY, e);
        }
    }

    private String tail(Path log) {
        try {
            String content = Files.readString(log, StandardCharsets.UTF_8);
            return content.length() <= LOG_TAIL_CHARS
                    ? content
                    : content.substring(content.length() - LOG_TAIL_CHARS);
        } catch (IOException e) {
            return "(no engine output captured)";
        }
    }
}
