package org.pdi.repository.rowmapper;

import org.pdi.web.models.GapReport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class GapReportRowMapper implements ResultSetExtractor<List<GapReport>> {

    @Override
    public List<GapReport> extractData(ResultSet rs) throws SQLException, DataAccessException {
        List<GapReport> result = new ArrayList<>();

        while (rs.next()) {
            GapReport gapReport = GapReport.builder()
                    .id(rs.getString("id"))
                    .boundaryCode(rs.getString("boundary_code"))
                    .campaignId(rs.getString("campaign_id"))
                    .estimatedPopulation(rs.getObject("estimated_population", Integer.class))
                    .registeredPopulation(rs.getObject("registered_population", Integer.class))
                    .populationGap(rs.getObject("population_gap", Integer.class))
                    .estimatedHouseholds(rs.getObject("estimated_households", Integer.class))
                    .registeredHouseholds(rs.getObject("registered_households", Integer.class))
                    .householdGap(rs.getObject("household_gap", Integer.class))
                    .coverageRatio(rs.getObject("coverage_ratio", Double.class))
                    .gapClassification(rs.getString("gap_classification"))
                    .riskScore(rs.getObject("risk_score", Integer.class))
                    .riskPriority(rs.getString("risk_priority"))
                    .tenantId(rs.getString("tenant_id"))
                    .build();
            result.add(gapReport);
        }

        return result;
    }
}
