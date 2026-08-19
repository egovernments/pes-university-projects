package org.pdi.service;

/**
 * A previously computed engine run, rehydrated from the Postgres result cache.
 * {@code response} is the stored {@code TargetComputeResponse} JSON (its URLs are
 * re-minted against a fresh artifact id on serve); the remaining fields are the
 * artifact file contents, any of which may be {@code null} when that artifact was
 * not produced (e.g. no sheet ⇒ no catchments/buildings/downloadable xlsx).
 */
public record CachedResult(String response,
                           String geojson,
                           String statsJson,
                           String settlements,
                           String catchments,
                           String buildings,
                           byte[] sheetXlsx) {
}
