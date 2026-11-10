package org.pdi.repository.rowmapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.pdi.web.models.BoundaryPopulation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class BoundaryPopulationRowMapper implements ResultSetExtractor<List<BoundaryPopulation>> {

    private static final TypeReference<LinkedHashMap<String, Integer>> POPULATION_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    @Autowired
    public BoundaryPopulationRowMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<BoundaryPopulation> extractData(ResultSet rs) throws SQLException, DataAccessException {
        List<BoundaryPopulation> result = new ArrayList<>();

        while (rs.next()) {
            BoundaryPopulation population = BoundaryPopulation.builder()
                    .id(rs.getString("id"))
                    .runId(rs.getString("run_id"))
                    .tenantId(rs.getString("tenant_id"))
                    .hierarchyType(rs.getString("hierarchy_type"))
                    .boundaryCode(rs.getString("boundary_code"))
                    .boundaryType(rs.getString("boundary_type"))
                    .parentBoundaryCode(rs.getString("parent_boundary_code"))
                    .population(readPopulation(rs.getString("population")))
                    .method(rs.getString("method"))
                    .areaKm2(rs.getObject("area_km2", Double.class))
                    .createdTime(rs.getObject("created_time", Long.class))
                    .build();
            result.add(population);
        }

        return result;
    }

    private Map<String, Integer> readPopulation(String json) throws SQLException {
        try {
            return json == null ? null : objectMapper.readValue(json, POPULATION_TYPE);
        } catch (JsonProcessingException e) {
            throw new SQLException("unreadable population: " + json, e);
        }
    }
}
