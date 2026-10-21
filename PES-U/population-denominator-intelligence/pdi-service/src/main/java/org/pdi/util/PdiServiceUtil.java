package org.pdi.util;

import org.pdi.exception.CustomException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

import static org.pdi.util.PdiConstants.ARTIFACT_ERROR;
import static org.pdi.util.PdiConstants.ENGINE_FAILED_MSG;
import static org.pdi.util.PdiConstants.WORKING_MESSAGE;

public class PdiServiceUtil {

    // The engine raises ordinary Python exceptions for bad uploads, so the last line of a
    // failed run reads "ValueError: this boundary file is not in TCD ...". The class name
    // is noise to whoever uploaded the file; the sentence after it is the actual message.
    private static final Pattern PYTHON_EXCEPTION =
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_.]*(Error|Exception):\\s+");

    private PdiServiceUtil() {
    }

    public static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String readableFailure(String line) {
        if (line == null || line.isBlank()) {
            return ENGINE_FAILED_MSG;
        }
        return PYTHON_EXCEPTION.matcher(line).replaceFirst("");
    }

    public static String lastLine(String text) {
        if (text == null || text.isBlank()) {
            return WORKING_MESSAGE;
        }
        String[] lines = text.strip().split("\\R");
        return lines[lines.length - 1].strip();
    }

    public static String readOrEmpty(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    public static Path writeText(Path dir, String name, String content) {
        if (content == null) {
            return null;
        }
        try {
            Path file = dir.resolve(name);
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new CustomException(ARTIFACT_ERROR, "could not stage cached artifact " + name,
                    HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    public static Path writeBytes(Path dir, String name, byte[] content) {
        if (content == null) {
            return null;
        }
        try {
            Path file = dir.resolve(name);
            Files.write(file, content);
            return file;
        } catch (IOException e) {
            throw new CustomException(ARTIFACT_ERROR, "could not stage cached artifact " + name,
                    HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    public static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    public static void deleteDirQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(PdiServiceUtil::deleteQuietly);
        } catch (IOException ignored) {
        }
    }

    public static Double round(Double value) {
        return value == null ? null : Math.round(value * 10000d) / 10000d;
    }

    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
