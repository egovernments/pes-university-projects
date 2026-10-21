package org.pdi.util;

public class PdiConstants {

    //Paths
    public static final String TARGETS_BASE_PATH = "/population/v1/targets/";
    public static final String STATUS_PATH = "/_status";
    public static final String DOWNLOAD_PATH = "/_download";
    public static final String GEOJSON_PATH = "/_geojson";
    public static final String SETTLEMENTS_PATH = "/_settlements";
    public static final String CATCHMENTS_PATH = "/_catchments";
    public static final String BUILDINGS_PATH = "/_buildings";
    public static final String STATS_PATH = "/_stats";

    //Artifact extensions
    public static final String XLSX_EXTENSION = "xlsx";
    public static final String GEOJSON_EXTENSION = "geojson";
    public static final String SETTLEMENTS_EXTENSION = "settlements.geojson";
    public static final String CATCHMENTS_EXTENSION = "catchments.geojson";
    public static final String BUILDINGS_EXTENSION = "buildings.geojson";
    public static final String STATS_EXTENSION = "stats.json";

    //Media types
    public static final String XLSX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    //General
    public static final String DEFAULT_TENANT_ID = "default";
    public static final String CAMPAIGN_ID_PREFIX = "PDI-";
    public static final String DEFAULT_CAMPAIGN_YEAR = "DEFAULT";
    public static final String BOUNDARY_CODE_KEY = "boundaryCode";
    public static final String PROGRESS_PREFIX = "PROGRESS ";
    public static final String WORKING_MESSAGE = "working";
    public static final String DOWNLOADING_MESSAGE = "downloading";
    public static final String ENGINE_LOG_FILE = "engine.log";
    public static final int LOG_TAIL_CHARS = 2000;

    //Dashboard
    public static final int TOP_GAP_LIMIT = 10;
    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final String DEFAULT_SORT_BY = "populationGap";
    public static final String SORT_ORDER_ASC = "ASC";

    //Error codes
    public static final String INVALID_FILE_TYPE = "INVALID_FILE_TYPE";
    public static final String ISO3_REQUIRED = "ISO3_REQUIRED";
    public static final String ISO3_REQUIRED_MSG = "iso3 is required";
    public static final String CAMPAIGN_ID_REQUIRED = "CAMPAIGN_ID_REQUIRED";
    public static final String CAMPAIGN_ID_REQUIRED_MSG = "campaignId is required";
    public static final String GAP_SEARCH_CAMPAIGN_ID_REQUIRED_MSG = "gapSearchCriteria.campaignId is required";
    public static final String JOB_NOT_FOUND = "JOB_NOT_FOUND";
    public static final String JOB_NOT_FOUND_MSG = "job not found";
    public static final String ARTIFACT_NOT_FOUND = "ARTIFACT_NOT_FOUND";
    public static final String TARGET_SHEET_NOT_FOUND_MSG = "target sheet not found";
    public static final String BOUNDARY_GEOJSON_NOT_FOUND_MSG = "boundary geojson not found";
    public static final String SETTLEMENTS_NOT_FOUND_MSG = "invisible settlements not found";
    public static final String CATCHMENTS_NOT_FOUND_MSG = "catchment cells not found";
    public static final String BUILDINGS_NOT_FOUND_MSG = "catchment buildings not found";
    public static final String STATS_NOT_FOUND_MSG = "dashboard stats not found";
    public static final String ENGINE_ERROR = "ENGINE_ERROR";
    public static final String ENGINE_FAILED_MSG = "The engine failed without reporting a reason. Check the API logs.";
    public static final String UPLOAD_ERROR = "UPLOAD_ERROR";
    public static final String ARTIFACT_ERROR = "ARTIFACT_ERROR";
}
