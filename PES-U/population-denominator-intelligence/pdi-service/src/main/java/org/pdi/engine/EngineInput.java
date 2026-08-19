package org.pdi.engine;

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
public enum EngineInput {

    BOUNDARIES("boundaries", ".geojson", "--boundaries"),
    ENUMERATION("enumeration", ".xlsx", "--enumeration"),
    SHEET("sheet", ".xlsx", "--sheet");

    private final String parameter;
    private final String extension;
    private final String engineFlag;

    EngineInput(String parameter, String extension, String engineFlag) {
        this.parameter = parameter;
        this.extension = extension;
        this.engineFlag = engineFlag;
    }

    /** The multipart request parameter this file arrives under. */
    public String parameter() {
        return parameter;
    }

    /** Suffix for the temporary file, so the engine's readers pick the right parser. */
    public String extension() {
        return extension;
    }

    /** The engine CLI flag this file is passed under. */
    public String engineFlag() {
        return engineFlag;
    }
}
