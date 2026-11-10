package org.pdi.repository.querybuilder;

import org.pdi.web.models.PopulationRunSearchCriteria;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

import static org.pdi.util.PdiConstants.STATUS_COMPLETED;
import static org.pdi.util.PdiConstants.STATUS_FAILED;
import static org.pdi.util.PdiConstants.STATUS_PENDING;
import static org.pdi.util.PdiConstants.STATUS_RUNNING;

@Component
public class PopulationRunQueryBuilder {

    public static final String INSERT_QUERY = "INSERT INTO eg_pdi_population_run " +
            "(id, tenant_id, hierarchy_type, iso3, year, groups, mode, status, is_active, progress, " +
            "created_by, created_time, last_modified_by, last_modified_time) " +
            "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, false, 0, ?, ?, ?, ?)";

    public static final String UPDATE_STATUS_QUERY = "UPDATE eg_pdi_population_run " +
            "SET status = ?, progress = ?, last_modified_time = ? WHERE id = ?";

    public static final String UPDATE_PROGRESS_QUERY = "UPDATE eg_pdi_population_run " +
            "SET progress = ?, last_modified_time = ? WHERE id = ? AND status = '" + STATUS_RUNNING + "'";

    public static final String FAIL_QUERY = "UPDATE eg_pdi_population_run " +
            "SET status = '" + STATUS_FAILED + "', error_message = ?, last_modified_time = ? WHERE id = ?";

    public static final String DEACTIVATE_QUERY = "UPDATE eg_pdi_population_run " +
            "SET is_active = false, last_modified_time = ? " +
            "WHERE tenant_id = ? AND hierarchy_type = ? AND is_active";

    public static final String COMPLETE_QUERY = "UPDATE eg_pdi_population_run " +
            "SET status = '" + STATUS_COMPLETED + "', is_active = ?, progress = 100, worldpop_release = ?, " +
            "boundary_count = ?, computed_count = ?, no_geometry_count = ?, last_modified_time = ? WHERE id = ?";

    public static final String FAIL_IN_FLIGHT_QUERY = "UPDATE eg_pdi_population_run " +
            "SET status = '" + STATUS_FAILED + "', error_message = ?, last_modified_time = ? " +
            "WHERE status IN ('" + STATUS_PENDING + "', '" + STATUS_RUNNING + "') AND created_time < ?";

    // Keeps the newest completed runs and the active one; in-flight runs are never pruned.
    public static final String PRUNE_QUERY = "DELETE FROM eg_pdi_population_run " +
            "WHERE tenant_id = ? AND hierarchy_type = ? AND NOT is_active " +
            "AND status IN ('" + STATUS_COMPLETED + "', '" + STATUS_FAILED + "') AND id NOT IN (" +
            "SELECT id FROM eg_pdi_population_run WHERE tenant_id = ? AND hierarchy_type = ? " +
            "AND status = '" + STATUS_COMPLETED + "' ORDER BY created_time DESC LIMIT ?)";

    private static final String FETCH_QUERY = "SELECT pr.id, pr.tenant_id, pr.hierarchy_type, pr.iso3, pr.year, " +
            "pr.worldpop_release, pr.groups, pr.mode, pr.status, pr.is_active, pr.progress, pr.boundary_count, " +
            "pr.computed_count, pr.no_geometry_count, pr.error_message, pr.created_by, pr.created_time, " +
            "pr.last_modified_by, pr.last_modified_time " +
            " FROM eg_pdi_population_run pr ";

    /* Constructs the query for the active run of a tenant and hierarchy type */
    public String getActiveRunQuery(String tenantId, String hierarchyType, List<Object> preparedStmtList) {
        preparedStmtList.add(tenantId);
        preparedStmtList.add(hierarchyType);
        return FETCH_QUERY + " WHERE pr.tenant_id = ? AND pr.hierarchy_type = ? AND pr.is_active";
    }

    /* Constructs the run search query, newest first */
    public String getSearchQuery(PopulationRunSearchCriteria criteria, List<Object> preparedStmtList) {
        StringBuilder queryBuilder = new StringBuilder(FETCH_QUERY);
        queryBuilder.append(" WHERE pr.tenant_id = ?");
        preparedStmtList.add(criteria.getTenantId());
        if (criteria.getHierarchyType() != null) {
            queryBuilder.append(" AND pr.hierarchy_type = ?");
            preparedStmtList.add(criteria.getHierarchyType());
        }
        if (criteria.getIds() != null && !criteria.getIds().isEmpty()) {
            queryBuilder.append(" AND pr.id IN (")
                    .append(String.join(", ", Collections.nCopies(criteria.getIds().size(), "?"))).append(")");
            preparedStmtList.addAll(criteria.getIds());
        }
        if (criteria.getStatus() != null) {
            queryBuilder.append(" AND pr.status = ?");
            preparedStmtList.add(criteria.getStatus());
        }
        if (criteria.getIsActive() != null) {
            queryBuilder.append(" AND pr.is_active = ?");
            preparedStmtList.add(criteria.getIsActive());
        }
        queryBuilder.append(" ORDER BY pr.created_time DESC LIMIT ? OFFSET ?");
        preparedStmtList.add(criteria.getLimit());
        preparedStmtList.add(criteria.getOffset());
        return queryBuilder.toString();
    }
}
