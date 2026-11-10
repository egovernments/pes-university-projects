package org.pdi.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.pdi.repository.querybuilder.BoundaryPopulationQueryBuilder;
import org.pdi.repository.rowmapper.BoundaryPopulationRowMapper;
import org.pdi.web.models.BoundaryPopulation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.pdi.repository.querybuilder.BoundaryPopulationQueryBuilder.INSERT_QUERY;
import static org.pdi.util.PdiConstants.TOTAL_GROUP;

/**
 * WorldPop population per HCM boundary code, one row per code per run.
 */
@Repository
public class BoundaryPopulationRepository {

    private final JdbcTemplate jdbcTemplate;

    private final BoundaryPopulationQueryBuilder queryBuilder;

    private final BoundaryPopulationRowMapper rowMapper;

    private final ObjectMapper objectMapper;

    @Autowired
    public BoundaryPopulationRepository(JdbcTemplate jdbcTemplate, BoundaryPopulationQueryBuilder queryBuilder,
                                        BoundaryPopulationRowMapper rowMapper, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.queryBuilder = queryBuilder;
        this.rowMapper = rowMapper;
        this.objectMapper = objectMapper;
    }

    public void saveAll(List<BoundaryPopulation> rows, int batchSize) {
        for (int from = 0; from < rows.size(); from += batchSize) {
            List<BoundaryPopulation> batch = rows.subList(from, Math.min(from + batchSize, rows.size()));
            List<Object[]> args = new ArrayList<>(batch.size());
            for (BoundaryPopulation row : batch) {
                Integer total = row.getPopulation() == null ? null : row.getPopulation().get(TOTAL_GROUP);
                args.add(new Object[]{
                        row.getId(), row.getRunId(), row.getTenantId(), row.getHierarchyType(),
                        row.getBoundaryCode(), row.getBoundaryType(), row.getParentBoundaryCode(),
                        total, toJson(row.getPopulation()), row.getMethod(), row.getAreaKm2(), row.getCreatedTime()});
            }
            jdbcTemplate.batchUpdate(INSERT_QUERY, args);
        }
    }

    public List<BoundaryPopulation> findByRun(String runId, Collection<String> boundaryCodes) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getByRunQuery(runId, boundaryCodes, preparedStmtList);
        return jdbcTemplate.query(query, rowMapper, preparedStmtList.toArray());
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("unserialisable population", e);
        }
    }
}
