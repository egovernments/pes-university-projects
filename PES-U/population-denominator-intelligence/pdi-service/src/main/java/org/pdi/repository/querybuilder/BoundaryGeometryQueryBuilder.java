package org.pdi.repository.querybuilder;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Component
public class BoundaryGeometryQueryBuilder {

    // An unchanged geometry keeps its last_modified_time, so re-uploading the same sheet
    // does not make the next DELTA run recompute anything.
    public static final String UPSERT_QUERY = "INSERT INTO eg_pdi_boundary_geometry " +
            "(id, tenant_id, hierarchy_type, boundary_code, geometry_type, geom, source, file_store_id, " +
            "created_by, created_time, last_modified_by, last_modified_time) " +
            "VALUES (?, ?, ?, ?, ?, ST_SetSRID(ST_GeomFromGeoJSON(?), 4326), ?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT (tenant_id, hierarchy_type, boundary_code) DO UPDATE SET " +
            "geometry_type = EXCLUDED.geometry_type, geom = EXCLUDED.geom, source = EXCLUDED.source, " +
            "file_store_id = EXCLUDED.file_store_id, last_modified_by = EXCLUDED.last_modified_by, " +
            "last_modified_time = EXCLUDED.last_modified_time " +
            "WHERE eg_pdi_boundary_geometry.geometry_type <> EXCLUDED.geometry_type " +
            "OR NOT ST_OrderingEquals(eg_pdi_boundary_geometry.geom, EXCLUDED.geom)";

    private static final String FETCH_QUERY = "SELECT bg.id, bg.tenant_id, bg.hierarchy_type, bg.boundary_code, " +
            "bg.geometry_type, bg.source, bg.file_store_id, bg.created_by, bg.created_time, " +
            "bg.last_modified_by, bg.last_modified_time, %s AS geometry " +
            " FROM eg_pdi_boundary_geometry bg ";

    /* Constructs the geometry search for a hierarchy, optionally limited to some codes */
    public String getSearchQuery(String tenantId, String hierarchyType, Collection<String> boundaryCodes,
                                 boolean includeGeometry, List<Object> preparedStmtList) {
        StringBuilder queryBuilder = new StringBuilder(
                String.format(FETCH_QUERY, includeGeometry ? "ST_AsGeoJSON(bg.geom)" : "NULL"));
        queryBuilder.append(" WHERE bg.tenant_id = ? AND bg.hierarchy_type = ?");
        preparedStmtList.add(tenantId);
        preparedStmtList.add(hierarchyType);
        if (boundaryCodes != null && !boundaryCodes.isEmpty()) {
            queryBuilder.append(" AND bg.boundary_code IN (").append(placeholders(boundaryCodes.size())).append(")");
            preparedStmtList.addAll(boundaryCodes);
        }
        queryBuilder.append(" ORDER BY bg.boundary_code");
        return queryBuilder.toString();
    }

    private String placeholders(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }
}
