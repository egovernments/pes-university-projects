package org.pdi.persistence.repository;

import org.pdi.persistence.entity.GapReport;
import org.pdi.persistence.projection.ClassificationCount;
import org.pdi.persistence.projection.CoverageProjection;
import org.pdi.persistence.projection.PriorityCount;
import org.pdi.persistence.projection.SummaryProjection;
import org.pdi.persistence.projection.TopGapProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GapReportRepository extends JpaRepository<GapReport, UUID> {

    @Query(value = """
            SELECT COALESCE(SUM(estimated_population), 0)   AS estimatedPopulation,
                   COALESCE(SUM(registered_population), 0)  AS registeredPopulation,
                   COALESCE(SUM(estimated_households), 0)   AS estimatedHouseholds,
                   COALESCE(SUM(registered_households), 0)  AS registeredHouseholds
            FROM gap_report
            WHERE campaign_id = :campaignId AND tenant_id = :tenantId
              AND (CAST(:boundaryCode AS text) IS NULL OR boundary_code = :boundaryCode)
            """, nativeQuery = true)
    SummaryProjection summarize(@Param("campaignId") String campaignId,
                                @Param("tenantId") String tenantId,
                                @Param("boundaryCode") String boundaryCode);

    @Query(value = """
            SELECT gap_classification              AS classification,
                   COUNT(*)                        AS count,
                   COALESCE(SUM(estimated_population), 0) AS population
            FROM gap_report
            WHERE campaign_id = :campaignId AND tenant_id = :tenantId
              AND (CAST(:boundaryCode AS text) IS NULL OR boundary_code = :boundaryCode)
              AND gap_classification IS NOT NULL
            GROUP BY gap_classification
            """, nativeQuery = true)
    List<ClassificationCount> gapDistribution(@Param("campaignId") String campaignId,
                                              @Param("tenantId") String tenantId,
                                              @Param("boundaryCode") String boundaryCode);

    @Query(value = """
            SELECT risk_priority AS priority, COUNT(*) AS count
            FROM gap_report
            WHERE campaign_id = :campaignId AND tenant_id = :tenantId
              AND (CAST(:boundaryCode AS text) IS NULL OR boundary_code = :boundaryCode)
              AND risk_priority IS NOT NULL
            GROUP BY risk_priority
            """, nativeQuery = true)
    List<PriorityCount> riskDistribution(@Param("campaignId") String campaignId,
                                         @Param("tenantId") String tenantId,
                                         @Param("boundaryCode") String boundaryCode);

    @Query(value = """
            SELECT sb.name            AS name,
                   gr.boundary_code   AS boundaryCode,
                   gr.population_gap  AS gap,
                   gr.coverage_ratio  AS coverageRatio,
                   gr.risk_score      AS riskScore,
                   gr.risk_priority   AS riskPriority
            FROM gap_report gr
            LEFT JOIN settlement_boundary sb
                   ON sb.boundary_code = gr.boundary_code AND sb.tenant_id = gr.tenant_id
            WHERE gr.campaign_id = :campaignId AND gr.tenant_id = :tenantId
              AND (CAST(:boundaryCode AS text) IS NULL OR gr.boundary_code = :boundaryCode)
            ORDER BY gr.population_gap DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<TopGapProjection> topGap(@Param("campaignId") String campaignId,
                                  @Param("tenantId") String tenantId,
                                  @Param("boundaryCode") String boundaryCode,
                                  @Param("limit") int limit);

    @Query(value = """
            SELECT sb.parent_boundary_code AS boundaryCode,
                   sb.parent_boundary_code AS name,
                   SUM(gr.registered_population)::double precision
                       / NULLIF(SUM(gr.estimated_population), 0) AS coverageRatio
            FROM gap_report gr
            JOIN settlement_boundary sb
              ON sb.boundary_code = gr.boundary_code AND sb.tenant_id = gr.tenant_id
            WHERE gr.campaign_id = :campaignId AND gr.tenant_id = :tenantId
              AND sb.parent_boundary_code IS NOT NULL
            GROUP BY sb.parent_boundary_code
            ORDER BY coverageRatio ASC NULLS LAST
            """, nativeQuery = true)
    List<CoverageProjection> coverageBySubBoundary(@Param("campaignId") String campaignId,
                                                   @Param("tenantId") String tenantId);

    @Query("""
            SELECT g FROM GapReport g
            WHERE g.campaignId = :campaignId AND g.tenantId = :tenantId
              AND (:boundaryCode IS NULL OR g.boundaryCode = :boundaryCode)
              AND (:classes IS NULL OR g.gapClassification IN :classes)
            """)
    Page<GapReport> search(@Param("campaignId") String campaignId,
                           @Param("tenantId") String tenantId,
                           @Param("boundaryCode") String boundaryCode,
                           @Param("classes") List<String> classes,
                           Pageable pageable);
}
