package org.pdi.repository;

import org.pdi.config.PdiConfiguration;
import org.pdi.exception.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

import static org.pdi.util.PdiConstants.ARTIFACT_ERROR;
import static org.pdi.util.PdiConstants.BUILDINGS_EXTENSION;
import static org.pdi.util.PdiConstants.CATCHMENTS_EXTENSION;
import static org.pdi.util.PdiConstants.GEOJSON_EXTENSION;
import static org.pdi.util.PdiConstants.SETTLEMENTS_EXTENSION;
import static org.pdi.util.PdiConstants.STATS_EXTENSION;
import static org.pdi.util.PdiConstants.XLSX_EXTENSION;

/**
 * File store for the artifacts of a compute run, named {@code <artifactId>.<extension>}
 * under the configured artifacts directory.
 */
@Repository
public class ArtifactRepository {

    private final Path root;

    @Autowired
    public ArtifactRepository(PdiConfiguration config) {
        this.root = Path.of(config.getArtifactsDir());
        ensureRoot();
    }

    public String store(Path sheet, Path geojson, Path settlements, Path catchments,
                        Path buildings, Path stats) {
        String jobId = UUID.randomUUID().toString();
        if (sheet != null) {
            move(sheet, jobId + "." + XLSX_EXTENSION);
        }
        if (geojson != null) {
            move(geojson, jobId + "." + GEOJSON_EXTENSION);
        }
        if (settlements != null) {
            move(settlements, jobId + "." + SETTLEMENTS_EXTENSION);
        }
        if (catchments != null) {
            move(catchments, jobId + "." + CATCHMENTS_EXTENSION);
        }
        if (buildings != null) {
            move(buildings, jobId + "." + BUILDINGS_EXTENSION);
        }
        if (stats != null) {
            move(stats, jobId + "." + STATS_EXTENSION);
        }
        return jobId;
    }

    public Optional<Path> find(String jobId, String extension) {
        Path file = root.resolve(jobId + "." + extension);
        return Files.exists(file) ? Optional.of(file) : Optional.empty();
    }

    private void move(Path source, String name) {
        try {
            Files.move(source, root.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new CustomException(ARTIFACT_ERROR, "could not store artifact " + name,
                    HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    private void ensureRoot() {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new CustomException(ARTIFACT_ERROR, "could not create artifact directory",
                    HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }
}
