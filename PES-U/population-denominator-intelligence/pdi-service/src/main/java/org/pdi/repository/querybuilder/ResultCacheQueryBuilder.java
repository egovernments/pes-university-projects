package org.pdi.repository.querybuilder;

import org.springframework.stereotype.Component;

@Component
public class ResultCacheQueryBuilder {

    private static final String CREATE_TABLE_QUERY = """
            CREATE TABLE IF NOT EXISTS engine_result_cache (
                cache_key    VARCHAR(64) PRIMARY KEY,
                iso3         VARCHAR(8)  NOT NULL,
                params       JSONB       NOT NULL,
                response     JSONB       NOT NULL,
                geojson      TEXT,
                stats_json   TEXT,
                settlements  TEXT,
                catchments   TEXT,
                buildings    TEXT,
                sheet_xlsx   BYTEA,
                created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
                last_hit_at  TIMESTAMPTZ,
                hit_count    INTEGER     NOT NULL DEFAULT 0
            )""";

    private static final String CREATE_INDEX_QUERY = "CREATE INDEX IF NOT EXISTS idx_engine_result_cache_iso3 " +
            "ON engine_result_cache (iso3)";

    private static final String FETCH_RESULT_QUERY = "SELECT response, geojson, stats_json, settlements, catchments, buildings, sheet_xlsx " +
            "FROM engine_result_cache WHERE cache_key = ?";

    private static final String RECORD_HIT_QUERY = "UPDATE engine_result_cache SET hit_count = hit_count + 1, last_hit_at = now() " +
            "WHERE cache_key = ?";

    private static final String UPSERT_RESULT_QUERY = "INSERT INTO engine_result_cache " +
            "(cache_key, iso3, params, response, geojson, stats_json, settlements, " +
            " catchments, buildings, sheet_xlsx) " +
            "VALUES (?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?) " +
            // A forced recompute overwrites the entry in place: the run is the
            // new truth for this key, so its artifacts and age replace the old
            // ones and the hit counters restart against the fresh result.
            "ON CONFLICT (cache_key) DO UPDATE SET " +
            "  iso3 = EXCLUDED.iso3, " +
            "  params = EXCLUDED.params, " +
            "  response = EXCLUDED.response, " +
            "  geojson = EXCLUDED.geojson, " +
            "  stats_json = EXCLUDED.stats_json, " +
            "  settlements = EXCLUDED.settlements, " +
            "  catchments = EXCLUDED.catchments, " +
            "  buildings = EXCLUDED.buildings, " +
            "  sheet_xlsx = EXCLUDED.sheet_xlsx, " +
            "  created_at = now(), " +
            "  last_hit_at = NULL, " +
            "  hit_count = 0";

    public String getCreateTableQuery() {
        return CREATE_TABLE_QUERY;
    }

    public String getCreateIndexQuery() {
        return CREATE_INDEX_QUERY;
    }

    public String getFetchResultQuery() {
        return FETCH_RESULT_QUERY;
    }

    public String getRecordHitQuery() {
        return RECORD_HIT_QUERY;
    }

    public String getUpsertResultQuery() {
        return UPSERT_RESULT_QUERY;
    }
}
