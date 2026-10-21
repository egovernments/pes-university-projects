package org.pdi.repository;

import org.pdi.repository.querybuilder.GapReportQueryBuilder;
import org.pdi.repository.rowmapper.CoverageEntryRowMapper;
import org.pdi.repository.rowmapper.GapClassificationCountRowMapper;
import org.pdi.repository.rowmapper.GapReportRowMapper;
import org.pdi.repository.rowmapper.GapTotalsRowMapper;
import org.pdi.repository.rowmapper.RiskPriorityCountRowMapper;
import org.pdi.repository.rowmapper.TopGapSettlementRowMapper;
import org.pdi.web.models.CoverageEntry;
import org.pdi.web.models.GapClassificationCount;
import org.pdi.web.models.GapReport;
import org.pdi.web.models.GapTotals;
import org.pdi.web.models.RiskPriorityCount;
import org.pdi.web.models.TopGapSettlement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Read access to the {@code gap_report} table the batch engine writes, joined to
 * {@code settlement_boundary} where names and parents are needed.
 */
@Repository
public class GapReportRepository {

    private final JdbcTemplate jdbcTemplate;

    private final GapReportQueryBuilder queryBuilder;

    private final GapReportRowMapper gapReportRowMapper;

    private final GapTotalsRowMapper gapTotalsRowMapper;

    private final GapClassificationCountRowMapper gapClassificationCountRowMapper;

    private final RiskPriorityCountRowMapper riskPriorityCountRowMapper;

    private final TopGapSettlementRowMapper topGapSettlementRowMapper;

    private final CoverageEntryRowMapper coverageEntryRowMapper;

    @Autowired
    public GapReportRepository(JdbcTemplate jdbcTemplate, GapReportQueryBuilder queryBuilder,
                               GapReportRowMapper gapReportRowMapper, GapTotalsRowMapper gapTotalsRowMapper,
                               GapClassificationCountRowMapper gapClassificationCountRowMapper,
                               RiskPriorityCountRowMapper riskPriorityCountRowMapper,
                               TopGapSettlementRowMapper topGapSettlementRowMapper,
                               CoverageEntryRowMapper coverageEntryRowMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.queryBuilder = queryBuilder;
        this.gapReportRowMapper = gapReportRowMapper;
        this.gapTotalsRowMapper = gapTotalsRowMapper;
        this.gapClassificationCountRowMapper = gapClassificationCountRowMapper;
        this.riskPriorityCountRowMapper = riskPriorityCountRowMapper;
        this.topGapSettlementRowMapper = topGapSettlementRowMapper;
        this.coverageEntryRowMapper = coverageEntryRowMapper;
    }

    public GapTotals summarize(String campaignId, String tenantId, String boundaryCode) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getSummaryQuery(campaignId, tenantId, boundaryCode, preparedStmtList);
        // An aggregate without GROUP BY always returns exactly one row.
        return jdbcTemplate.query(query, gapTotalsRowMapper, preparedStmtList.toArray()).get(0);
    }

    public List<GapClassificationCount> gapDistribution(String campaignId, String tenantId, String boundaryCode) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getGapDistributionQuery(campaignId, tenantId, boundaryCode, preparedStmtList);
        return jdbcTemplate.query(query, gapClassificationCountRowMapper, preparedStmtList.toArray());
    }

    public List<RiskPriorityCount> riskDistribution(String campaignId, String tenantId, String boundaryCode) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getRiskDistributionQuery(campaignId, tenantId, boundaryCode, preparedStmtList);
        return jdbcTemplate.query(query, riskPriorityCountRowMapper, preparedStmtList.toArray());
    }

    public List<TopGapSettlement> topGap(String campaignId, String tenantId, String boundaryCode, int limit) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getTopGapQuery(campaignId, tenantId, boundaryCode, limit, preparedStmtList);
        return jdbcTemplate.query(query, topGapSettlementRowMapper, preparedStmtList.toArray());
    }

    public List<CoverageEntry> coverageBySubBoundary(String campaignId, String tenantId) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getCoverageBySubBoundaryQuery(campaignId, tenantId, preparedStmtList);
        return jdbcTemplate.query(query, coverageEntryRowMapper, preparedStmtList.toArray());
    }

    public List<GapReport> search(String campaignId, String tenantId, String boundaryCode, List<String> classes,
                                  String sortBy, boolean ascending, int limit, int offset) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getGapReportSearchQuery(campaignId, tenantId, boundaryCode, classes,
                sortBy, ascending, limit, offset, preparedStmtList);
        return jdbcTemplate.query(query, gapReportRowMapper, preparedStmtList.toArray());
    }

    public long count(String campaignId, String tenantId, String boundaryCode, List<String> classes) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getGapReportCountQuery(campaignId, tenantId, boundaryCode, classes, preparedStmtList);
        Long count = jdbcTemplate.queryForObject(query, Long.class, preparedStmtList.toArray());
        return count == null ? 0 : count;
    }

    public boolean isSortable(String sortBy) {
        return queryBuilder.isSortable(sortBy);
    }
}
