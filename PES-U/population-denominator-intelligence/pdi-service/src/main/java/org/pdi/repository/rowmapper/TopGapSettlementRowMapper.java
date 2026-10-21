package org.pdi.repository.rowmapper;

import org.pdi.web.models.TopGapSettlement;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class TopGapSettlementRowMapper implements ResultSetExtractor<List<TopGapSettlement>> {

    @Override
    public List<TopGapSettlement> extractData(ResultSet rs) throws SQLException, DataAccessException {
        List<TopGapSettlement> result = new ArrayList<>();

        while (rs.next()) {
            TopGapSettlement topGapSettlement = TopGapSettlement.builder()
                    .name(rs.getString("name"))
                    .boundaryCode(rs.getString("boundary_code"))
                    .gap(rs.getLong("gap"))
                    .coverageRatio(rs.getObject("coverage_ratio", Double.class))
                    .riskScore(rs.getObject("risk_score", Integer.class))
                    .riskPriority(rs.getString("risk_priority"))
                    .build();
            result.add(topGapSettlement);
        }

        return result;
    }
}
