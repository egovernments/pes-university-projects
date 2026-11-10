package org.pdi.repository;

import org.pdi.repository.querybuilder.BoundaryGeometryQueryBuilder;
import org.pdi.repository.rowmapper.BoundaryGeometryRowMapper;
import org.pdi.web.models.BoundaryGeometry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.pdi.repository.querybuilder.BoundaryGeometryQueryBuilder.UPSERT_QUERY;

/**
 * Locations collected for HCM boundary codes through the PDI geometry sheet.
 */
@Repository
public class BoundaryGeometryRepository {

    private final JdbcTemplate jdbcTemplate;

    private final BoundaryGeometryQueryBuilder queryBuilder;

    private final BoundaryGeometryRowMapper rowMapper;

    @Autowired
    public BoundaryGeometryRepository(JdbcTemplate jdbcTemplate, BoundaryGeometryQueryBuilder queryBuilder,
                                      BoundaryGeometryRowMapper rowMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.queryBuilder = queryBuilder;
        this.rowMapper = rowMapper;
    }

    /** Inserts or replaces each location and returns how many rows actually changed. */
    public int upsert(List<BoundaryGeometry> geometries, int batchSize) {
        int changed = 0;
        for (int from = 0; from < geometries.size(); from += batchSize) {
            List<BoundaryGeometry> batch = geometries.subList(from, Math.min(from + batchSize, geometries.size()));
            List<Object[]> args = new ArrayList<>(batch.size());
            for (BoundaryGeometry geometry : batch) {
                args.add(new Object[]{
                        geometry.getId(), geometry.getTenantId(), geometry.getHierarchyType(),
                        geometry.getBoundaryCode(), geometry.getGeometryType(), geometry.getGeometry().toString(),
                        geometry.getSource(), geometry.getFileStoreId(),
                        geometry.getAuditDetails().getCreatedBy(), geometry.getAuditDetails().getCreatedTime(),
                        geometry.getAuditDetails().getLastModifiedBy(), geometry.getAuditDetails().getLastModifiedTime()});
            }
            changed += Arrays.stream(jdbcTemplate.batchUpdate(UPSERT_QUERY, args)).sum();
        }
        return changed;
    }

    public List<BoundaryGeometry> search(String tenantId, String hierarchyType, Collection<String> boundaryCodes,
                                         boolean includeGeometry) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getSearchQuery(tenantId, hierarchyType, boundaryCodes, includeGeometry,
                preparedStmtList);
        return jdbcTemplate.query(query, rowMapper, preparedStmtList.toArray());
    }
}
