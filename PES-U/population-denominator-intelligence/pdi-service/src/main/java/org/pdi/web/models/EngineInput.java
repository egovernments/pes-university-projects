package org.pdi.web.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A file the caller can upload for a compute run.
 *
 * <p>{@link #BOUNDARIES} carries the catchment polygons and facility anchors; it defines the
 * units every figure is computed over. {@link #ENUMERATION} carries what the field actually
 * recorded per facility, and is what turns the coverage and risk layers on. Both are optional:
 * without boundaries the units are the country's own districts, and without an enumeration
 * there is nothing to compare against.
 *
 * <p>{@link #SHEET} is the earlier single-upload path, which derived Voronoi cells from a
 * sheet of facility coordinates. It is superseded by {@link #BOUNDARIES} - a country that
 * already drew its catchments should send them rather than have us infer them - and is kept
 * only so existing callers do not break.
 */
@Getter
@AllArgsConstructor
public enum EngineInput {

    BOUNDARIES("boundaries", ".geojson", "--boundaries"),
    ENUMERATION("enumeration", ".xlsx", "--enumeration"),
    SHEET("sheet", ".xlsx", "--sheet");

    // The multipart request parameter this file arrives under.
    private final String parameter;

    // Suffix for the temporary file, so the engine's readers pick the right parser.
    private final String extension;

    // The engine CLI flag this file is passed under.
    private final String engineFlag;
}
