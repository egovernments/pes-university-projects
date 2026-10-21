package org.pdi.repository;

import lombok.extern.slf4j.Slf4j;
import org.pdi.config.PdiConfiguration;
import org.pdi.repository.querybuilder.ResultCacheQueryBuilder;
import org.pdi.repository.rowmapper.CachedResultRowMapper;
import org.pdi.web.models.CachedResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
@Slf4j
@Repository
public class ResultCacheRepository {

    private final ObjectProvider<JdbcTemplate> jdbcProvider;

    private final ResultCacheQueryBuilder queryBuilder;

    private final CachedResultRowMapper rowMapper;

    private final boolean enabledByConfig;

    private volatile boolean available;

    private volatile boolean initialized;

    @Autowired
    public ResultCacheRepository(ObjectProvider<JdbcTemplate> jdbcProvider, ResultCacheQueryBuilder queryBuilder,
                                 CachedResultRowMapper rowMapper, PdiConfiguration config) {
        this.jdbcProvider = jdbcProvider;
        this.queryBuilder = queryBuilder;
        this.rowMapper = rowMapper;
        this.enabledByConfig = config.getCacheEnabled();
        this.available = config.getCacheEnabled();
    }

    /** Look up a cached run, or empty when absent or the cache is unavailable. */
    public Optional<CachedResult> find(String cacheKey) {
        JdbcTemplate jdbc = ready();
        if (jdbc == null) {
            return Optional.empty();
        }
        try {
            List<CachedResult> rows = jdbc.query(queryBuilder.getFetchResultQuery(), rowMapper, cacheKey);
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            jdbc.update(queryBuilder.getRecordHitQuery(), cacheKey);
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
            jdbc.update(queryBuilder.getUpsertResultQuery(),
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
                        jdbc.execute(queryBuilder.getCreateTableQuery());
                        jdbc.execute(queryBuilder.getCreateIndexQuery());
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

    private void disable(String phase, RuntimeException e) {
        if (available) {
            log.warn("Result cache disabled after {} failure; computes will run fresh: {}",
                    phase, e.getMessage());
        }
        available = false;
    }
}
