package org.pdi.repository.rowmapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.pdi.web.models.AuditDetails;
import org.pdi.web.models.PopulationRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class PopulationRunRowMapper implements ResultSetExtractor<List<PopulationRun>> {

    private static final TypeReference<List<String>> GROUPS_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    @Autowired
    public PopulationRunRowMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<PopulationRun> extractData(ResultSet rs) throws SQLException, DataAccessException {
        List<PopulationRun> result = new ArrayList<>();

        while (rs.next()) {
            AuditDetails auditDetails = AuditDetails.builder()
                    .createdBy(rs.getString("created_by"))
                    .createdTime(rs.getObject("created_time", Long.class))
                    .lastModifiedBy(rs.getString("last_modified_by"))
                    .lastModifiedTime(rs.getObject("last_modified_time", Long.class))
                    .build();
            PopulationRun run = PopulationRun.builder()
                    .id(rs.getString("id"))
                    .tenantId(rs.getString("tenant_id"))
                    .hierarchyType(rs.getString("hierarchy_type"))
                    .iso3(rs.getString("iso3"))
                    .year(rs.getObject("year", Integer.class))
                    .worldpopRelease(rs.getString("worldpop_release"))
                    .groups(readGroups(rs.getString("groups")))
                    .mode(rs.getString("mode"))
                    .status(rs.getString("status"))
                    .isActive(rs.getBoolean("is_active"))
                    .progress(rs.getObject("progress", Integer.class))
                    .boundaryCount(rs.getObject("boundary_count", Integer.class))
                    .computedCount(rs.getObject("computed_count", Integer.class))
                    .noGeometryCount(rs.getObject("no_geometry_count", Integer.class))
                    .errorMessage(rs.getString("error_message"))
                    .auditDetails(auditDetails)
                    .build();
            result.add(run);
        }

        return result;
    }

    private List<String> readGroups(String json) throws SQLException {
        try {
            return json == null ? List.of() : objectMapper.readValue(json, GROUPS_TYPE);
        } catch (JsonProcessingException e) {
            throw new SQLException("unreadable run groups: " + json, e);
        }
    }
}
