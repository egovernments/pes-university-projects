package org.pdi.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.pdi.config.EngineProperties;
import org.pdi.service.Uploads;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class PythonEngineRunner {

    private static final int LOG_TAIL_CHARS = 2000;

    private final EngineProperties properties;
    private final ObjectMapper objectMapper;

    public PythonEngineRunner(EngineProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
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
                properties.python(), "-u", "-m", "features.targets",
                "--iso3", command.iso3(),
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
            args.add(input.engineFlag());
            args.add(staged.toAbsolutePath().toString());
            hasUnits |= input == EngineInput.BOUNDARIES || input == EngineInput.SHEET;
        }
        // The workbook is only produced when there are catchment units to describe.
        if (hasUnits) {
            args.add("--out");
            args.add(outSheet.toString());
        }
        if (command.year() != null) {
            args.add("--year");
            args.add(command.year().toString());
        }
        if (command.householdSize() != null) {
            args.add("--household-size");
            args.add(command.householdSize().toString());
        }
        if (command.groups() != null && !command.groups().isBlank()) {
            args.add("--groups");
            args.add(command.groups());
        }
        if (!command.withBuildings()) {
            args.add("--no-buildings");
        }
        if (command.campaignId() != null && !command.campaignId().isBlank()) {
            args.add("--persist");
            args.add("--campaign-id");
            args.add(command.campaignId());
            args.add("--tenant-id");
            args.add(command.tenantId() == null ? "default" : command.tenantId());
            if (properties.persistBuildings()) {
                args.add("--persist-buildings");
            }
        }
        return args;
    }

    private Process start(List<String> args, Path log) {
        ProcessBuilder builder = new ProcessBuilder(args)
                .directory(new File(properties.workdir()))
                .redirectErrorStream(true)
                .redirectOutput(log.toFile());
        try {
            return builder.start();
        } catch (IOException e) {
            throw new EngineException("could not start the PDI engine process", e);
        }
    }

    private void awaitCompletion(Process process, Path log) {
        try {
            long timeout = properties.timeoutSeconds();
            if (timeout <= 0) {
                // No cap: the first run for a country downloads WorldPop rasters
                // (and can take hours on a slow link). The job is async and streams
                // progress to the log, so we let the batch run to completion.
                process.waitFor();
            } else {
                boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    throw new EngineException("engine timed out after " + timeout + "s");
                }
            }
            if (process.exitValue() != 0) {
                throw new EngineException("engine failed: " + tail(log));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EngineException("engine run was interrupted", e);
        }
    }

    private EngineResult readResult(Path resultJson) {
        try {
            return objectMapper.readValue(resultJson.toFile(), EngineResult.class);
        } catch (IOException e) {
            throw new EngineException("could not read engine result", e);
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
