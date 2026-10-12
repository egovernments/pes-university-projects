package org.pdi.service;

import org.pdi.persistence.entity.GapReport;
import org.pdi.persistence.projection.ClassificationCount;
import org.pdi.persistence.projection.PriorityCount;
import org.pdi.persistence.projection.SummaryProjection;
import org.pdi.persistence.repository.GapReportRepository;
import org.pdi.persistence.repository.InvisibleSettlementRepository;
import org.pdi.web.dto.DashboardStatsResponse;
import org.pdi.web.dto.DashboardStatsResponse.CoverageEntry;
import org.pdi.web.dto.DashboardStatsResponse.GapBucket;
import org.pdi.web.dto.DashboardStatsResponse.Summary;
import org.pdi.web.dto.DashboardStatsResponse.TopGapSettlement;
import org.pdi.web.dto.GapSearchRequest.GapSearchCriteria;
import org.pdi.web.dto.GapSearchResponse;
import org.pdi.web.dto.GapSearchResponse.GapReportItem;
import org.pdi.web.dto.GapSearchResponse.Pagination;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the pre-computed PostGIS tables and assembles the documented dashboard and
 * gap-search payloads. No raster maths happens here; the batch engine writes the
 * tables and this service only aggregates and shapes them.
 */
@Service
public class DashboardService {

    private static final int TOP_GAP_LIMIT = 10;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final Set<String> SORTABLE =
            Set.of("coverageRatio", "populationGap", "riskScore", "estimatedPopulation");

    private final GapReportRepository gapReports;
    private final InvisibleSettlementRepository invisibleSettlements;

    public DashboardService(GapReportRepository gapReports,
                            InvisibleSettlementRepository invisibleSettlements) {
        this.gapReports = gapReports;
        this.invisibleSettlements = invisibleSettlements;
    }

    @Transactional(readOnly = true)
    public DashboardStatsResponse stats(String campaignId, String boundaryCode, String tenantId) {
        String boundary = blankToNull(boundaryCode);

        SummaryProjection totals = gapReports.summarize(campaignId, tenantId, boundary);
        long estimatedPopulation = totals.getEstimatedPopulation();
        long registeredPopulation = totals.getRegisteredPopulation();
        long estimatedHouseholds = totals.getEstimatedHouseholds();
        long registeredHouseholds = totals.getRegisteredHouseholds();
        Double coverage = estimatedPopulation > 0
                ? round((double) registeredPopulation / estimatedPopulation)
                : null;

        Summary summary = new Summary(
                estimatedPopulation,
                registeredPopulation,
                estimatedPopulation - registeredPopulation,
                coverage,
                estimatedHouseholds,
                registeredHouseholds,
                estimatedHouseholds - registeredHouseholds,
                invisibleSettlements.countByTenantId(tenantId),
                invisibleSettlements.sumEstimatedPopulation(tenantId));

        Map<String, GapBucket> gapDistribution = new LinkedHashMap<>();
        for (ClassificationCount row : gapReports.gapDistribution(campaignId, tenantId, boundary)) {
            gapDistribution.put(row.getClassification(), new GapBucket(row.getCount(), row.getPopulation()));
        }

        Map<String, Long> riskDistribution = new LinkedHashMap<>();
        for (PriorityCount row : gapReports.riskDistribution(campaignId, tenantId, boundary)) {
            riskDistribution.put(row.getPriority(), row.getCount());
        }

        List<TopGapSettlement> topGap = gapReports.topGap(campaignId, tenantId, boundary, TOP_GAP_LIMIT)
                .stream()
                .map(t -> new TopGapSettlement(t.getName(), t.getBoundaryCode(), t.getGap(),
                        round(t.getCoverageRatio()), t.getRiskScore(), t.getRiskPriority()))
                .toList();

        List<CoverageEntry> coverageBySubBoundary =
                gapReports.coverageBySubBoundary(campaignId, tenantId)
                        .stream()
                        .map(c -> new CoverageEntry(c.getName(), c.getBoundaryCode(), round(c.getCoverageRatio())))
                        .toList();

        return new DashboardStatsResponse(campaignId, boundaryCode, summary,
                gapDistribution, topGap, coverageBySubBoundary, riskDistribution);
    }

    @Transactional(readOnly = true)
    public GapSearchResponse search(GapSearchCriteria criteria, String tenantId) {
        int limit = criteria.limit() != null && criteria.limit() > 0 ? criteria.limit() : DEFAULT_PAGE_SIZE;
        int offset = criteria.offset() != null && criteria.offset() > 0 ? criteria.offset() : 0;
        List<String> classes = criteria.gapClassification() == null || criteria.gapClassification().isEmpty()
                ? null : criteria.gapClassification();

        PageRequest page = PageRequest.of(offset / limit, limit, sortOf(criteria));
        Page<GapReport> results = gapReports.search(
                criteria.campaignId(), tenantId, blankToNull(criteria.boundaryCode()), classes, page);

        List<GapReportItem> items = results.getContent().stream().map(this::toItem).toList();
        return new GapSearchResponse(items, results.getTotalElements(), new Pagination(limit, offset));
    }

    private GapReportItem toItem(GapReport g) {
        return new GapReportItem(
                g.getId().toString(),
                g.getBoundaryCode(),
                g.getEstimatedPopulation(),
                g.getRegisteredPopulation(),
                g.getPopulationGap(),
                g.getEstimatedHouseholds(),
                g.getRegisteredHouseholds(),
                g.getHouseholdGap(),
                g.getCoverageRatio(),
                g.getGapClassification(),
                g.getRiskScore(),
                g.getRiskPriority());
    }

    private Sort sortOf(GapSearchCriteria criteria) {
        String property = SORTABLE.contains(criteria.sortBy()) ? criteria.sortBy() : "populationGap";
        Sort.Direction direction = "ASC".equalsIgnoreCase(criteria.sortOrder())
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }

    private Double round(Double value) {
        return value == null ? null : Math.round(value * 10000d) / 10000d;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
