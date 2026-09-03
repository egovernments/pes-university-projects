package org.pdi.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gap_report")
public class GapReport {

    @Id
    private UUID id;

    @Column(name = "boundary_code")
    private String boundaryCode;

    @Column(name = "campaign_id")
    private String campaignId;

    @Column(name = "estimated_population")
    private Integer estimatedPopulation;

    @Column(name = "registered_population")
    private Integer registeredPopulation;

    @Column(name = "population_gap")
    private Integer populationGap;

    @Column(name = "estimated_households")
    private Integer estimatedHouseholds;

    @Column(name = "registered_households")
    private Integer registeredHouseholds;

    @Column(name = "household_gap")
    private Integer householdGap;

    @Column(name = "coverage_ratio")
    private Double coverageRatio;

    @Column(name = "gap_classification")
    private String gapClassification;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "risk_priority")
    private String riskPriority;

    @Column(name = "computed_at")
    private Instant computedAt;

    @Column(name = "tenant_id")
    private String tenantId;

    public UUID getId() {
        return id;
    }

    public String getBoundaryCode() {
        return boundaryCode;
    }

    public String getCampaignId() {
        return campaignId;
    }

    public Integer getEstimatedPopulation() {
        return estimatedPopulation;
    }

    public Integer getRegisteredPopulation() {
        return registeredPopulation;
    }

    public Integer getPopulationGap() {
        return populationGap;
    }

    public Integer getEstimatedHouseholds() {
        return estimatedHouseholds;
    }

    public Integer getRegisteredHouseholds() {
        return registeredHouseholds;
    }

    public Integer getHouseholdGap() {
        return householdGap;
    }

    public Double getCoverageRatio() {
        return coverageRatio;
    }

    public String getGapClassification() {
        return gapClassification;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public String getRiskPriority() {
        return riskPriority;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public String getTenantId() {
        return tenantId;
    }
}
