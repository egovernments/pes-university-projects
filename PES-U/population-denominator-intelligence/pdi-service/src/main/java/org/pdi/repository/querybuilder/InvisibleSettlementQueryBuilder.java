package org.pdi.repository.querybuilder;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InvisibleSettlementQueryBuilder {

    private static final String COUNT_INVISIBLE_SETTLEMENT_QUERY = "SELECT COUNT(*) FROM invisible_settlement i ";

    private static final String SUM_ESTIMATED_POPULATION_QUERY = "SELECT COALESCE(SUM(i.estimated_population), 0) FROM invisible_settlement i ";

    /* Constructs the invisible settlement count query for a tenant */
    public String getCountQuery(String tenantId, List<Object> preparedStmtList) {
        StringBuilder queryBuilder = new StringBuilder(COUNT_INVISIBLE_SETTLEMENT_QUERY);
        addTenantClause(queryBuilder, tenantId, preparedStmtList);
        return queryBuilder.toString();
    }

    /* Constructs the summed estimated population query for a tenant */
    public String getEstimatedPopulationQuery(String tenantId, List<Object> preparedStmtList) {
        StringBuilder queryBuilder = new StringBuilder(SUM_ESTIMATED_POPULATION_QUERY);
        addTenantClause(queryBuilder, tenantId, preparedStmtList);
        return queryBuilder.toString();
    }

    private void addTenantClause(StringBuilder queryBuilder, String tenantId, List<Object> preparedStmtList) {
        queryBuilder.append(" WHERE i.tenant_id = ? ");
        preparedStmtList.add(tenantId);
    }
}
