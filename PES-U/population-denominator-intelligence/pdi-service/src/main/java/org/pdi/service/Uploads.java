package org.pdi.service;

import org.pdi.engine.EngineInput;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The files uploaded with one compute request, staged on disk for the engine process.
 *
 * <p>Each file is hashed on arrival. Those hashes are what make the result cache correct
 * across multiple inputs: two runs are the same run only when every uploaded file is
 * byte-identical, so re-uploading a corrected enumeration sheet against unchanged boundaries
 * misses the cache and recomputes, as it must.
 *
 * <p>Staged files are temporary and the caller must {@link #deleteAll()} when the run ends.
 */
public final class Uploads {

    private final Map<EngineInput, Path> staged;
    private final Map<EngineInput, String> hashes;

    private Uploads(Map<EngineInput, Path> staged, Map<EngineInput, String> hashes) {
        this.staged = staged;
        this.hashes = hashes;
    }

    /** Read, validate and stage every non-empty upload. */
    public static Uploads stage(Map<EngineInput, MultipartFile> incoming) {
        Map<EngineInput, Path> staged = new EnumMap<>(EngineInput.class);
        Map<EngineInput, String> hashes = new EnumMap<>(EngineInput.class);
        for (Map.Entry<EngineInput, MultipartFile> entry : incoming.entrySet()) {
            MultipartFile file = entry.getValue();
            if (file == null || file.isEmpty()) {
                continue;
            }
            EngineInput input = entry.getKey();
            byte[] content = read(input, file);
            hashes.put(input, sha256Hex(content));
            staged.put(input, write(input, content));
        }
        return new Uploads(staged, hashes);
    }

    /** An empty set - no files uploaded, so the engine runs on the country's own data. */
    public static Uploads none() {
        return new Uploads(new EnumMap<>(EngineInput.class), new EnumMap<>(EngineInput.class));
    }

    public Path path(EngineInput input) {
        return staged.get(input);
    }

    public boolean has(EngineInput input) {
        return staged.containsKey(input);
    }

    public boolean isEmpty() {
        return staged.isEmpty();
    }

    /**
     * A stable identity for this exact set of files, for the cache key. Absent inputs are
     * named explicitly so "boundaries only" and "enumeration only" can never collide.
     */
    public String fingerprint() {
        StringBuilder builder = new StringBuilder();
        for (EngineInput input : EngineInput.values()) {
            builder.append(input.name()).append('=')
                    .append(hashes.getOrDefault(input, "none")).append(';');
        }
        return builder.toString();
    }

    /** Per-file hashes, recorded alongside the cached result so a run can be traced back. */
    public Map<String, String> hashes() {
        Map<String, String> result = new LinkedHashMap<>();
        hashes.forEach((input, hash) -> result.put(input.parameter(), hash));
        return result;
    }

    public void deleteAll() {
        staged.values().forEach(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // A leftover temp file is not worth failing a completed run over.
            }
        });
    }

    private static byte[] read(EngineInput input, MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("could not read the uploaded " + input.parameter(), e);
        }
    }

    private static Path write(EngineInput input, byte[] content) {
        try {
            Path target = Files.createTempFile("pdi-" + input.parameter() + "-", input.extension());
            Files.write(target, content);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException("could not store the uploaded " + input.parameter(), e);
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Whether the uploaded file's name looks like what this input expects. An empty or
     * unnamed file passes - it is either absent or the caller sent no filename, and neither
     * is grounds to reject a request. The web layer decides what to do with a {@code false}.
     */
    public static boolean hasExpectedExtension(EngineInput input, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return true;
        }
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return true;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return switch (input) {
            case BOUNDARIES -> lower.endsWith(".geojson") || lower.endsWith(".json");
            case ENUMERATION, SHEET -> lower.endsWith(".xlsx") || lower.endsWith(".xls");
        };
    }
}
