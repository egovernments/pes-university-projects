package org.pdi.repository;

import org.pdi.repository.querybuilder.InvisibleSettlementQueryBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Read access to the {@code invisible_settlement} table the batch engine writes. The
 * dashboard needs only counts and population totals, not the geometry columns.
 */
@Repository
public class InvisibleSettlementRepository {

    private final JdbcTemplate jdbcTemplate;

    private final InvisibleSettlementQueryBuilder queryBuilder;

    @Autowired
    public InvisibleSettlementRepository(JdbcTemplate jdbcTemplate, InvisibleSettlementQueryBuilder queryBuilder) {
        this.jdbcTemplate = jdbcTemplate;
        this.queryBuilder = queryBuilder;
    }

    public long countByTenantId(String tenantId) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getCountQuery(tenantId, preparedStmtList);
        Long count = jdbcTemplate.queryForObject(query, Long.class, preparedStmtList.toArray());
        return count == null ? 0 : count;
    }

    public long sumEstimatedPopulation(String tenantId) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getEstimatedPopulationQuery(tenantId, preparedStmtList);
        Long population = jdbcTemplate.queryForObject(query, Long.class, preparedStmtList.toArray());
        return population == null ? 0 : population;
    }
}
