package org.pdi.service;

import org.pdi.config.CacheProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Postgres-backed cache of whole engine runs. A run is deterministic for a given
 * country + parameter set, so once computed we persist its artifacts here keyed by
 * a request hash and serve repeats from the database instead of re-running the
 * batch engine (which re-does WorldPop zonal statistics and the VIDA building
 * cross-check every time).
 *
 * <p>The cache is strictly best-effort: any database problem (unreachable, missing
 * table, transient error) disables it for the process and every request falls back
 * to a fresh engine run. It never propagates a failure into the compute path.
 */
@Component
public class ResultCacheStore {

    private static final Logger log = LoggerFactory.getLogger(ResultCacheStore.class);

    private final ObjectProvider<JdbcTemplate> jdbcProvider;
    private final boolean enabledByConfig;

    private volatile boolean available;
    private volatile boolean initialized;

    public ResultCacheStore(ObjectProvider<JdbcTemplate> jdbcProvider, CacheProperties properties) {
        this.jdbcProvider = jdbcProvider;
        this.enabledByConfig = properties.enabled();
        this.available = properties.enabled();
    }

    /** Look up a cached run, or empty when absent or the cache is unavailable. */
    public Optional<CachedResult> find(String cacheKey) {
        JdbcTemplate jdbc = ready();
        if (jdbc == null) {
            return Optional.empty();
        }
        try {
            List<CachedResult> rows = jdbc.query(
                    "SELECT response, geojson, stats_json, settlements, catchments, buildings, sheet_xlsx "
                            + "FROM engine_result_cache WHERE cache_key = ?",
                    (rs, n) -> new CachedResult(
                            rs.getString("response"),
                            rs.getString("geojson"),
                            rs.getString("stats_json"),
                            rs.getString("settlements"),
                            rs.getString("catchments"),
                            rs.getString("buildings"),
                            rs.getBytes("sheet_xlsx")),
                    cacheKey);
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            jdbc.update("UPDATE engine_result_cache SET hit_count = hit_count + 1, last_hit_at = now() "
                    + "WHERE cache_key = ?", cacheKey);
            return Optional.of(rows.get(0));
        } catch (RuntimeException e) {
            disable("read", e);
            return Optional.empty();
        }
    }

    /** Persist a freshly computed run. Silently no-ops when the cache is unavailable. */
    public void save(String cacheKey, String iso3, String paramsJson, String responseJson,
                     String geojson, String statsJson, String settlements, String catchments,
                     String buildings, byte[] sheetXlsx) {
        JdbcTemplate jdbc = ready();
        if (jdbc == null) {
            return;
        }
        try {
            jdbc.update(
                    "INSERT INTO engine_result_cache "
                            + "(cache_key, iso3, params, response, geojson, stats_json, settlements, "
                            + " catchments, buildings, sheet_xlsx) "
                            + "VALUES (?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?) "
                            // A forced recompute overwrites the entry in place: the run is the
                            // new truth for this key, so its artifacts and age replace the old
                            // ones and the hit counters restart against the fresh result.
                            + "ON CONFLICT (cache_key) DO UPDATE SET "
                            + "  iso3 = EXCLUDED.iso3, "
                            + "  params = EXCLUDED.params, "
                            + "  response = EXCLUDED.response, "
                            + "  geojson = EXCLUDED.geojson, "
                            + "  stats_json = EXCLUDED.stats_json, "
                            + "  settlements = EXCLUDED.settlements, "
                            + "  catchments = EXCLUDED.catchments, "
                            + "  buildings = EXCLUDED.buildings, "
                            + "  sheet_xlsx = EXCLUDED.sheet_xlsx, "
                            + "  created_at = now(), "
                            + "  last_hit_at = NULL, "
                            + "  hit_count = 0",
                    cacheKey, iso3, paramsJson, responseJson, geojson, statsJson,
                    settlements, catchments, buildings, sheetXlsx);
        } catch (RuntimeException e) {
            disable("write", e);
        }
    }

    /** Return a usable JdbcTemplate, initializing the schema once, or null if unavailable. */
    private JdbcTemplate ready() {
        if (!available) {
            return null;
        }
        JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
        if (jdbc == null) {
            if (enabledByConfig) {
                log.info("Result cache disabled: no DataSource configured.");
            }
            available = false;
            return null;
        }
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    try {
                        ensureSchema(jdbc);
                        initialized = true;
                        log.info("Result cache ready (Postgres engine_result_cache).");
                    } catch (RuntimeException e) {
                        disable("initialize", e);
                        return null;
                    }
                }
            }
        }
        return available ? jdbc : null;
    }

    private void ensureSchema(JdbcTemplate jdbc) {
        jdbc.execute("""
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
                )""");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_engine_result_cache_iso3 "
                + "ON engine_result_cache (iso3)");
    }

    private void disable(String phase, RuntimeException e) {
        if (available) {
            log.warn("Result cache disabled after {} failure; computes will run fresh: {}",
                    phase, e.getMessage());
        }
        available = false;
    }
}
