package org.pdi.repository.rowmapper;

import org.pdi.web.models.GapClassificationCount;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class GapClassificationCountRowMapper implements ResultSetExtractor<List<GapClassificationCount>> {

    @Override
    public List<GapClassificationCount> extractData(ResultSet rs) throws SQLException, DataAccessException {
        List<GapClassificationCount> result = new ArrayList<>();

        while (rs.next()) {
            GapClassificationCount gapClassificationCount = GapClassificationCount.builder()
                    .classification(rs.getString("classification"))
                    .count(rs.getLong("count"))
                    .population(rs.getLong("population"))
                    .build();
            result.add(gapClassificationCount);
        }

        return result;
    }
}
