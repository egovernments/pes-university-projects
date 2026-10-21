package org.pdi.service;

import lombok.extern.slf4j.Slf4j;
import org.pdi.repository.GapReportRepository;
import org.pdi.repository.InvisibleSettlementRepository;
import org.pdi.validator.dashboard.DashboardValidator;
import org.pdi.web.models.CoverageEntry;
import org.pdi.web.models.DashboardStatsResponse;
import org.pdi.web.models.DashboardSummary;
import org.pdi.web.models.GapBucket;
import org.pdi.web.models.GapClassificationCount;
import org.pdi.web.models.GapReport;
import org.pdi.web.models.GapReportItem;
import org.pdi.web.models.GapSearchCriteria;
import org.pdi.web.models.GapSearchRequest;
import org.pdi.web.models.GapSearchResponse;
import org.pdi.web.models.GapTotals;
import org.pdi.web.models.Pagination;
import org.pdi.web.models.RiskPriorityCount;
import org.pdi.web.models.TopGapSettlement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.pdi.util.PdiConstants.DEFAULT_PAGE_SIZE;
import static org.pdi.util.PdiConstants.DEFAULT_SORT_BY;
import static org.pdi.util.PdiConstants.SORT_ORDER_ASC;
import static org.pdi.util.PdiConstants.TOP_GAP_LIMIT;
import static org.pdi.util.PdiServiceUtil.blankToNull;
import static org.pdi.util.PdiServiceUtil.round;

/**
 * Reads the pre-computed PostGIS tables and assembles the documented dashboard and
 * gap-search payloads. No raster maths happens here; the batch engine writes the
 * tables and this service only aggregates and shapes them.
 */
@Slf4j
@Service
public class DashboardService {

    private final GapReportRepository gapReportRepository;

    private final InvisibleSettlementRepository invisibleSettlementRepository;

    private final DashboardValidator dashboardValidator;

    @Autowired
    public DashboardService(GapReportRepository gapReportRepository,
                            InvisibleSettlementRepository invisibleSettlementRepository,
                            DashboardValidator dashboardValidator) {
        this.gapReportRepository = gapReportRepository;
        this.invisibleSettlementRepository = invisibleSettlementRepository;
        this.dashboardValidator = dashboardValidator;
    }

    public DashboardStatsResponse stats(String campaignId, String boundaryCode, String tenantId) {
        dashboardValidator.validateStatsRequest(campaignId);
        log.info("Fetching dashboard stats for campaign {}", campaignId);
        String boundary = blankToNull(boundaryCode);

        GapTotals totals = gapReportRepository.summarize(campaignId, tenantId, boundary);
        long estimatedPopulation = totals.getEstimatedPopulation();
        long registeredPopulation = totals.getRegisteredPopulation();
        long estimatedHouseholds = totals.getEstimatedHouseholds();
        long registeredHouseholds = totals.getRegisteredHouseholds();
        Double coverage = estimatedPopulation > 0
                ? round((double) registeredPopulation / estimatedPopulation)
                : null;

        DashboardSummary summary = DashboardSummary.builder()
                .totalEstimatedPopulation(estimatedPopulation)
                .totalRegisteredPopulation(registeredPopulation)
                .totalPopulationGap(estimatedPopulation - registeredPopulation)
                .overallCoverageRatio(coverage)
                .totalEstimatedHouseholds(estimatedHouseholds)
                .totalRegisteredHouseholds(registeredHouseholds)
                .householdGap(estimatedHouseholds - registeredHouseholds)
                .invisibleSettlementCount(invisibleSettlementRepository.countByTenantId(tenantId))
                .invisibleEstimatedPopulation(invisibleSettlementRepository.sumEstimatedPopulation(tenantId))
                .build();

        Map<String, GapBucket> gapDistribution = new LinkedHashMap<>();
        for (GapClassificationCount row : gapReportRepository.gapDistribution(campaignId, tenantId, boundary)) {
            gapDistribution.put(row.getClassification(), new GapBucket(row.getCount(), row.getPopulation()));
        }

        Map<String, Long> riskDistribution = new LinkedHashMap<>();
        for (RiskPriorityCount row : gapReportRepository.riskDistribution(campaignId, tenantId, boundary)) {
            riskDistribution.put(row.getPriority(), row.getCount());
        }

        List<TopGapSettlement> topGap = gapReportRepository.topGap(campaignId, tenantId, boundary, TOP_GAP_LIMIT)
                .stream()
                .map(t -> new TopGapSettlement(t.getName(), t.getBoundaryCode(), t.getGap(),
                        round(t.getCoverageRatio()), t.getRiskScore(), t.getRiskPriority()))
                .toList();

        List<CoverageEntry> coverageBySubBoundary =
                gapReportRepository.coverageBySubBoundary(campaignId, tenantId)
                        .stream()
                        .map(c -> new CoverageEntry(c.getName(), c.getBoundaryCode(), round(c.getCoverageRatio())))
                        .toList();

        return DashboardStatsResponse.builder()
                .campaignId(campaignId)
                .boundaryCode(boundaryCode)
                .summary(summary)
                .gapDistribution(gapDistribution)
                .topGapSettlements(topGap)
                .coverageBySubBoundary(coverageBySubBoundary)
                .riskDistribution(riskDistribution)
                .build();
    }

    public GapSearchResponse search(GapSearchRequest request, String tenantId) {
        dashboardValidator.validateGapSearchRequest(request);
        GapSearchCriteria criteria = request.getGapSearchCriteria();
        log.info("Searching gap reports for campaign {}", criteria.getCampaignId());
        int limit = criteria.getLimit() != null && criteria.getLimit() > 0 ? criteria.getLimit() : DEFAULT_PAGE_SIZE;
        int offset = criteria.getOffset() != null && criteria.getOffset() > 0 ? criteria.getOffset() : 0;
        List<String> classes = criteria.getGapClassification() == null || criteria.getGapClassification().isEmpty()
                ? null : criteria.getGapClassification();
        String boundaryCode = blankToNull(criteria.getBoundaryCode());
        String sortBy = gapReportRepository.isSortable(criteria.getSortBy()) ? criteria.getSortBy() : DEFAULT_SORT_BY;
        boolean ascending = SORT_ORDER_ASC.equalsIgnoreCase(criteria.getSortOrder());

        // Rows are fetched a whole page at a time: an offset that is not a multiple of the
        // limit starts at the beginning of the page that contains it.
        int pageOffset = (offset / limit) * limit;
        List<GapReport> results = gapReportRepository.search(
                criteria.getCampaignId(), tenantId, boundaryCode, classes, sortBy, ascending, limit, pageOffset);
        long totalCount = gapReportRepository.count(criteria.getCampaignId(), tenantId, boundaryCode, classes);

        List<GapReportItem> items = results.stream().map(this::toItem).toList();
        return GapSearchResponse.builder()
                .gapReports(items)
                .totalCount(totalCount)
                .pagination(new Pagination(limit, offset))
                .build();
    }

    private GapReportItem toItem(GapReport g) {
        return GapReportItem.builder()
                .settlementId(g.getId())
                .boundaryCode(g.getBoundaryCode())
                .estimatedPopulation(g.getEstimatedPopulation())
                .registeredPopulation(g.getRegisteredPopulation())
                .populationGap(g.getPopulationGap())
                .estimatedHouseholds(g.getEstimatedHouseholds())
                .registeredHouseholds(g.getRegisteredHouseholds())
                .householdGap(g.getHouseholdGap())
                .coverageRatio(g.getCoverageRatio())
                .gapClassification(g.getGapClassification())
                .riskScore(g.getRiskScore())
                .riskPriority(g.getRiskPriority())
                .build();
    }
}
