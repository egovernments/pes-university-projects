package org.pdi.repository.rowmapper;

import org.pdi.web.models.RiskPriorityCount;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class RiskPriorityCountRowMapper implements ResultSetExtractor<List<RiskPriorityCount>> {

    @Override
    public List<RiskPriorityCount> extractData(ResultSet rs) throws SQLException, DataAccessException {
        List<RiskPriorityCount> result = new ArrayList<>();

        while (rs.next()) {
            RiskPriorityCount riskPriorityCount = RiskPriorityCount.builder()
                    .priority(rs.getString("priority"))
                    .count(rs.getLong("count"))
                    .build();
            result.add(riskPriorityCount);
        }

        return result;
    }
}
