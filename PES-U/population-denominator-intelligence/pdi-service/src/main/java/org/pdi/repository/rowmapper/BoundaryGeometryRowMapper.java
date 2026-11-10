package org.pdi.repository.rowmapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.pdi.web.models.AuditDetails;
import org.pdi.web.models.BoundaryGeometry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class BoundaryGeometryRowMapper implements ResultSetExtractor<List<BoundaryGeometry>> {

    private final ObjectMapper objectMapper;

    @Autowired
    public BoundaryGeometryRowMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<BoundaryGeometry> extractData(ResultSet rs) throws SQLException, DataAccessException {
        List<BoundaryGeometry> result = new ArrayList<>();

        while (rs.next()) {
            AuditDetails auditDetails = AuditDetails.builder()
                    .createdBy(rs.getString("created_by"))
                    .createdTime(rs.getObject("created_time", Long.class))
                    .lastModifiedBy(rs.getString("last_modified_by"))
                    .lastModifiedTime(rs.getObject("last_modified_time", Long.class))
                    .build();
            BoundaryGeometry geometry = BoundaryGeometry.builder()
                    .id(rs.getString("id"))
                    .tenantId(rs.getString("tenant_id"))
                    .hierarchyType(rs.getString("hierarchy_type"))
                    .boundaryCode(rs.getString("boundary_code"))
                    .geometryType(rs.getString("geometry_type"))
                    .geometry(readGeometry(rs.getString("geometry")))
                    .source(rs.getString("source"))
                    .fileStoreId(rs.getString("file_store_id"))
                    .auditDetails(auditDetails)
                    .build();
            result.add(geometry);
        }

        return result;
    }

    private JsonNode readGeometry(String json) throws SQLException {
        try {
            return json == null ? null : objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new SQLException("unreadable geometry", e);
        }
    }
}
