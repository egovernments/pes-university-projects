package org.pdi.repository.querybuilder;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Component
public class BoundaryPopulationQueryBuilder {

    public static final String INSERT_QUERY = "INSERT INTO eg_pdi_boundary_population " +
            "(id, run_id, tenant_id, hierarchy_type, boundary_code, boundary_type, parent_boundary_code, " +
            "total_population, population, method, area_km2, created_time) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)";

    private static final String FETCH_QUERY = "SELECT bp.id, bp.run_id, bp.tenant_id, bp.hierarchy_type, " +
            "bp.boundary_code, bp.boundary_type, bp.parent_boundary_code, bp.population, bp.method, " +
            "bp.area_km2, bp.created_time " +
            " FROM eg_pdi_boundary_population bp ";

    /* Constructs the query for a run's rows, optionally limited to some boundary codes */
    public String getByRunQuery(String runId, Collection<String> boundaryCodes, List<Object> preparedStmtList) {
        StringBuilder queryBuilder = new StringBuilder(FETCH_QUERY);
        queryBuilder.append(" WHERE bp.run_id = ?");
        preparedStmtList.add(runId);
        if (boundaryCodes != null && !boundaryCodes.isEmpty()) {
            queryBuilder.append(" AND bp.boundary_code IN (")
                    .append(String.join(", ", Collections.nCopies(boundaryCodes.size(), "?"))).append(")");
            preparedStmtList.addAll(boundaryCodes);
        }
        return queryBuilder.toString();
    }
}
