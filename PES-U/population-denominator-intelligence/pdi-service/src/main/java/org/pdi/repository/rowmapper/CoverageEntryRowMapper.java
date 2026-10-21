package org.pdi.repository.rowmapper;

import org.pdi.web.models.CoverageEntry;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class CoverageEntryRowMapper implements ResultSetExtractor<List<CoverageEntry>> {

    @Override
    public List<CoverageEntry> extractData(ResultSet rs) throws SQLException, DataAccessException {
        List<CoverageEntry> result = new ArrayList<>();

        while (rs.next()) {
            CoverageEntry coverageEntry = CoverageEntry.builder()
                    .name(rs.getString("name"))
                    .boundaryCode(rs.getString("boundary_code"))
                    .coverageRatio(rs.getObject("coverage_ratio", Double.class))
                    .build();
            result.add(coverageEntry);
        }

        return result;
    }
}
