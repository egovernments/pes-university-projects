package org.pdi.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.pdi.repository.querybuilder.PopulationRunQueryBuilder;
import org.pdi.repository.rowmapper.PopulationRunRowMapper;
import org.pdi.web.models.PopulationRun;
import org.pdi.web.models.PopulationRunSearchCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.pdi.repository.querybuilder.PopulationRunQueryBuilder.COMPLETE_QUERY;
import static org.pdi.repository.querybuilder.PopulationRunQueryBuilder.DEACTIVATE_QUERY;
import static org.pdi.repository.querybuilder.PopulationRunQueryBuilder.FAIL_IN_FLIGHT_QUERY;
import static org.pdi.repository.querybuilder.PopulationRunQueryBuilder.FAIL_QUERY;
import static org.pdi.repository.querybuilder.PopulationRunQueryBuilder.INSERT_QUERY;
import static org.pdi.repository.querybuilder.PopulationRunQueryBuilder.PRUNE_QUERY;
import static org.pdi.repository.querybuilder.PopulationRunQueryBuilder.UPDATE_PROGRESS_QUERY;
import static org.pdi.repository.querybuilder.PopulationRunQueryBuilder.UPDATE_STATUS_QUERY;

/**
 * WorldPop runs per tenant and hierarchy type. The run row is the job's source of truth:
 * status and progress survive a restart.
 */
@Repository
public class PopulationRunRepository {

    private final JdbcTemplate jdbcTemplate;

    private final PopulationRunQueryBuilder queryBuilder;

    private final PopulationRunRowMapper rowMapper;

    private final ObjectMapper objectMapper;

    @Autowired
    public PopulationRunRepository(JdbcTemplate jdbcTemplate, PopulationRunQueryBuilder queryBuilder,
                                   PopulationRunRowMapper rowMapper, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.queryBuilder = queryBuilder;
        this.rowMapper = rowMapper;
        this.objectMapper = objectMapper;
    }

    /** Inserts a PENDING run; the in-flight unique index rejects a second one per hierarchy. */
    public void create(PopulationRun run) {
        jdbcTemplate.update(INSERT_QUERY, run.getId(), run.getTenantId(), run.getHierarchyType(), run.getIso3(),
                run.getYear(), toJson(run.getGroups()), run.getMode(), run.getStatus(),
                run.getAuditDetails().getCreatedBy(), run.getAuditDetails().getCreatedTime(),
                run.getAuditDetails().getLastModifiedBy(), run.getAuditDetails().getLastModifiedTime());
    }

    public void updateStatus(String id, String status, int progress, long time) {
        jdbcTemplate.update(UPDATE_STATUS_QUERY, status, progress, time, id);
    }

    public void updateProgress(String id, int progress, long time) {
        jdbcTemplate.update(UPDATE_PROGRESS_QUERY, progress, time, id);
    }

    public void fail(String id, String message, long time) {
        jdbcTemplate.update(FAIL_QUERY, message, time, id);
    }

    public void deactivate(String tenantId, String hierarchyType, long time) {
        jdbcTemplate.update(DEACTIVATE_QUERY, time, tenantId, hierarchyType);
    }

    public void complete(PopulationRun run, boolean activate, long time) {
        jdbcTemplate.update(COMPLETE_QUERY, activate, run.getWorldpopRelease(), run.getBoundaryCount(),
                run.getComputedCount(), run.getNoGeometryCount(), time, run.getId());
    }

    public int failInFlightCreatedBefore(long createdBefore, String message, long time) {
        return jdbcTemplate.update(FAIL_IN_FLIGHT_QUERY, message, time, createdBefore);
    }

    public int prune(String tenantId, String hierarchyType, int keep) {
        return jdbcTemplate.update(PRUNE_QUERY, tenantId, hierarchyType, tenantId, hierarchyType, keep);
    }

    public Optional<PopulationRun> findActive(String tenantId, String hierarchyType) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getActiveRunQuery(tenantId, hierarchyType, preparedStmtList);
        return first(jdbcTemplate.query(query, rowMapper, preparedStmtList.toArray()));
    }

    public List<PopulationRun> search(PopulationRunSearchCriteria criteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getSearchQuery(criteria, preparedStmtList);
        return jdbcTemplate.query(query, rowMapper, preparedStmtList.toArray());
    }

    private Optional<PopulationRun> first(List<PopulationRun> runs) {
        return runs == null || runs.isEmpty() ? Optional.empty() : Optional.of(runs.get(0));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("unserialisable run value", e);
        }
    }
}
