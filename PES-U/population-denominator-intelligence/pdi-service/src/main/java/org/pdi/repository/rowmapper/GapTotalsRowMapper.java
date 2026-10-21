package org.pdi.repository.rowmapper;

import org.pdi.web.models.GapTotals;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class GapTotalsRowMapper implements ResultSetExtractor<List<GapTotals>> {

    @Override
    public List<GapTotals> extractData(ResultSet rs) throws SQLException, DataAccessException {
        List<GapTotals> result = new ArrayList<>();

        while (rs.next()) {
            GapTotals gapTotals = GapTotals.builder()
                    .estimatedPopulation(rs.getLong("estimated_population"))
                    .registeredPopulation(rs.getLong("registered_population"))
                    .estimatedHouseholds(rs.getLong("estimated_households"))
                    .registeredHouseholds(rs.getLong("registered_households"))
                    .build();
            result.add(gapTotals);
        }

        return result;
    }
}
