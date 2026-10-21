package org.pdi.repository.rowmapper;

import org.pdi.web.models.CachedResult;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class CachedResultRowMapper implements ResultSetExtractor<List<CachedResult>> {

    @Override
    public List<CachedResult> extractData(ResultSet rs) throws SQLException, DataAccessException {
        List<CachedResult> result = new ArrayList<>();

        while (rs.next()) {
            CachedResult cachedResult = CachedResult.builder()
                    .response(rs.getString("response"))
                    .geojson(rs.getString("geojson"))
                    .statsJson(rs.getString("stats_json"))
                    .settlements(rs.getString("settlements"))
                    .catchments(rs.getString("catchments"))
                    .buildings(rs.getString("buildings"))
                    .sheetXlsx(rs.getBytes("sheet_xlsx"))
                    .build();
            result.add(cachedResult);
        }

        return result;
    }
}
