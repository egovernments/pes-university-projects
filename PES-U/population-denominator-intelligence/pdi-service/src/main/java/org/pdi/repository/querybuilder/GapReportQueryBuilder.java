package org.pdi.repository.querybuilder;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
public class GapReportQueryBuilder {

    private static final String FETCH_SUMMARY_QUERY = "SELECT COALESCE(SUM(gr.estimated_population), 0) AS estimated_population, " +
            "COALESCE(SUM(gr.registered_population), 0) AS registered_population, " +
            "COALESCE(SUM(gr.estimated_households), 0) AS estimated_households, " +
            "COALESCE(SUM(gr.registered_households), 0) AS registered_households " +
            " FROM gap_report gr ";

    private static final String FETCH_GAP_DISTRIBUTION_QUERY = "SELECT gr.gap_classification AS classification, COUNT(*) AS count, " +
            "COALESCE(SUM(gr.estimated_population), 0) AS population " +
            " FROM gap_report gr ";

    private static final String FETCH_RISK_DISTRIBUTION_QUERY = "SELECT gr.risk_priority AS priority, COUNT(*) AS count " +
            " FROM gap_report gr ";

    private static final String FETCH_TOP_GAP_QUERY = "SELECT sb.name AS name, gr.boundary_code AS boundary_code, " +
            "gr.population_gap AS gap, gr.coverage_ratio AS coverage_ratio, gr.risk_score AS risk_score, gr.risk_priority AS risk_priority " +
            " FROM gap_report gr " +
            " LEFT JOIN settlement_boundary sb ON sb.boundary_code = gr.boundary_code AND sb.tenant_id = gr.tenant_id ";

    private static final String FETCH_COVERAGE_BY_SUB_BOUNDARY_QUERY = "SELECT sb.parent_boundary_code AS boundary_code, sb.parent_boundary_code AS name, " +
            "SUM(gr.registered_population)::double precision / NULLIF(SUM(gr.estimated_population), 0) AS coverage_ratio " +
            " FROM gap_report gr " +
            " JOIN settlement_boundary sb ON sb.boundary_code = gr.boundary_code AND sb.tenant_id = gr.tenant_id ";

    private static final String FETCH_GAP_REPORT_QUERY = "SELECT gr.id, gr.boundary_code, gr.campaign_id, gr.estimated_population, gr.registered_population, " +
            "gr.population_gap, gr.estimated_households, gr.registered_households, gr.household_gap, gr.coverage_ratio, " +
            "gr.gap_classification, gr.risk_score, gr.risk_priority, gr.tenant_id " +
            " FROM gap_report gr ";

    private static final String COUNT_GAP_REPORT_QUERY = "SELECT COUNT(*) FROM gap_report gr ";

    // API sort keys to the columns they order by.
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "coverageRatio", "gr.coverage_ratio",
            "populationGap", "gr.population_gap",
            "riskScore", "gr.risk_score",
            "estimatedPopulation", "gr.estimated_population");

    /* Constructs the population/household totals query for a campaign, optionally one boundary */
    public String getSummaryQuery(String campaignId, String tenantId, String boundaryCode, List<Object> preparedStmtList) {
        StringBuilder queryBuilder = new StringBuilder(FETCH_SUMMARY_QUERY);
        addCampaignClauses(queryBuilder, campaignId, tenantId, boundaryCode, preparedStmtList);
        return queryBuilder.toString();
    }

    /* Constructs the per-classification count and population query */
    public String getGapDistributionQuery(String campaignId, String tenantId, String boundaryCode, List<Object> preparedStmtList) {
        StringBuilder queryBuilder = new StringBuilder(FETCH_GAP_DISTRIBUTION_QUERY);
        addCampaignClauses(queryBuilder, campaignId, tenantId, boundaryCode, preparedStmtList);
        queryBuilder.append(" AND gr.gap_classification IS NOT NULL");
        queryBuilder.append(" GROUP BY gr.gap_classification");
        return queryBuilder.toString();
    }

    /* Constructs the per-risk-priority count query */
    public String getRiskDistributionQuery(String campaignId, String tenantId, String boundaryCode, List<Object> preparedStmtList) {
        StringBuilder queryBuilder = new StringBuilder(FETCH_RISK_DISTRIBUTION_QUERY);
        addCampaignClauses(queryBuilder, campaignId, tenantId, boundaryCode, preparedStmtList);
        queryBuilder.append(" AND gr.risk_priority IS NOT NULL");
        queryBuilder.append(" GROUP BY gr.risk_priority");
        return queryBuilder.toString();
    }

    /* Constructs the largest-population-gap settlements query */
    public String getTopGapQuery(String campaignId, String tenantId, String boundaryCode, int limit, List<Object> preparedStmtList) {
        StringBuilder queryBuilder = new StringBuilder(FETCH_TOP_GAP_QUERY);
        addCampaignClauses(queryBuilder, campaignId, tenantId, boundaryCode, preparedStmtList);
        queryBuilder.append(" ORDER BY gr.population_gap DESC");
        queryBuilder.append(" LIMIT ?");
        preparedStmtList.add(limit);
        return queryBuilder.toString();
    }

    /* Constructs the coverage ratio per parent boundary query, worst covered first */
    public String getCoverageBySubBoundaryQuery(String campaignId, String tenantId, List<Object> preparedStmtList) {
        StringBuilder queryBuilder = new StringBuilder(FETCH_COVERAGE_BY_SUB_BOUNDARY_QUERY);
        addCampaignClauses(queryBuilder, campaignId, tenantId, null, preparedStmtList);
        queryBuilder.append(" AND sb.parent_boundary_code IS NOT NULL");
        queryBuilder.append(" GROUP BY sb.parent_boundary_code");
        queryBuilder.append(" ORDER BY coverage_ratio ASC NULLS LAST");
        return queryBuilder.toString();
    }

    /* Constructs the gap report search query with sorting and paging */
    public String getGapReportSearchQuery(String campaignId, String tenantId, String boundaryCode, List<String> classes,
                                          String sortBy, boolean ascending, int limit, int offset,
                                          List<Object> preparedStmtList) {
        StringBuilder queryBuilder = new StringBuilder(FETCH_GAP_REPORT_QUERY);
        addSearchClauses(queryBuilder, campaignId, tenantId, boundaryCode, classes, preparedStmtList);
        queryBuilder.append(" ORDER BY ").append(SORT_COLUMNS.get(sortBy)).append(ascending ? " ASC" : " DESC");
        queryBuilder.append(" LIMIT ? OFFSET ?");
        preparedStmtList.add(limit);
        preparedStmtList.add(offset);
        return queryBuilder.toString();
    }

    /* Constructs the total count query matching the gap report search */
    public String getGapReportCountQuery(String campaignId, String tenantId, String boundaryCode, List<String> classes,
                                         List<Object> preparedStmtList) {
        StringBuilder queryBuilder = new StringBuilder(COUNT_GAP_REPORT_QUERY);
        addSearchClauses(queryBuilder, campaignId, tenantId, boundaryCode, classes, preparedStmtList);
        return queryBuilder.toString();
    }

    public boolean isSortable(String sortBy) {
        return sortBy != null && SORT_COLUMNS.containsKey(sortBy);
    }

    private void addSearchClauses(StringBuilder queryBuilder, String campaignId, String tenantId, String boundaryCode,
                                  List<String> classes, List<Object> preparedStmtList) {
        addCampaignClauses(queryBuilder, campaignId, tenantId, boundaryCode, preparedStmtList);
        if (classes != null && !classes.isEmpty()) {
            queryBuilder.append(" AND gr.gap_classification IN (").append(createQuery(classes)).append(")");
            addToPreparedStatement(preparedStmtList, classes);
        }
    }

    private void addCampaignClauses(StringBuilder queryBuilder, String campaignId, String tenantId, String boundaryCode,
                                    List<Object> preparedStmtList) {
        addClauseIfRequired(preparedStmtList, queryBuilder);
        queryBuilder.append(" gr.campaign_id = ? ");
        preparedStmtList.add(campaignId);

        addClauseIfRequired(preparedStmtList, queryBuilder);
        queryBuilder.append(" gr.tenant_id = ? ");
        preparedStmtList.add(tenantId);

        if (boundaryCode != null) {
            addClauseIfRequired(preparedStmtList, queryBuilder);
            queryBuilder.append(" gr.boundary_code = ? ");
            preparedStmtList.add(boundaryCode);
        }
    }

    private static void addClauseIfRequired(List<Object> values, StringBuilder queryString) {
        if (values.isEmpty())
            queryString.append(" WHERE ");
        else {
            queryString.append(" AND");
        }
    }

    private String createQuery(Collection<String> ids) {
        StringBuilder builder = new StringBuilder();
        int length = ids.size();
        for (int i = 0; i < length; i++) {
            builder.append(" ? ");
            if (i != length - 1) builder.append(",");
        }
        return builder.toString();
    }

    private void addToPreparedStatement(List<Object> preparedStmtList, Collection<String> ids) {
        preparedStmtList.addAll(ids);
    }
}
