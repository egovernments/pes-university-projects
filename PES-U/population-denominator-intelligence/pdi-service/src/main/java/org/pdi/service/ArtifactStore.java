package org.pdi.service;

import org.pdi.config.ArtifactProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

@Component
public class ArtifactStore {

    private final Path root;

    public ArtifactStore(ArtifactProperties properties) {
        this.root = Path.of(properties.dir());
        ensureRoot();
    }

    public String store(Path sheet, Path geojson, Path settlements, Path catchments,
                        Path buildings, Path stats) {
        String jobId = UUID.randomUUID().toString();
        if (sheet != null) {
            move(sheet, jobId + ".xlsx");
        }
        if (geojson != null) {
            move(geojson, jobId + ".geojson");
        }
        if (settlements != null) {
            move(settlements, jobId + ".settlements.geojson");
        }
        if (catchments != null) {
            move(catchments, jobId + ".catchments.geojson");
        }
        if (buildings != null) {
            move(buildings, jobId + ".buildings.geojson");
        }
        if (stats != null) {
            move(stats, jobId + ".stats.json");
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
            throw new UncheckedIOException("could not store artifact " + name, e);
        }
    }

    private void ensureRoot() {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("could not create artifact directory", e);
        }
    }
}
