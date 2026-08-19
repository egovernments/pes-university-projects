package org.pdi.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "population_estimate")
public class PopulationEstimate {

    @Id
    private UUID id;

    @Column(name = "boundary_code")
    private String boundaryCode;

    @Column(name = "estimated_population")
    private Integer estimatedPopulation;

    @Column(name = "estimated_households")
    private Integer estimatedHouseholds;

    @Column(name = "building_count")
    private Integer buildingCount;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "method")
    private String method;

    @Column(name = "worldpop_version")
    private String worldpopVersion;

    @Column(name = "open_buildings_version")
    private String openBuildingsVersion;

    @Column(name = "population_density")
    private Double populationDensity;

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

    public Integer getEstimatedPopulation() {
        return estimatedPopulation;
    }

    public Integer getEstimatedHouseholds() {
        return estimatedHouseholds;
    }

    public Integer getBuildingCount() {
        return buildingCount;
    }

    public Double getConfidence() {
        return confidence;
    }

    public String getMethod() {
        return method;
    }

    public String getWorldpopVersion() {
        return worldpopVersion;
    }

    public String getOpenBuildingsVersion() {
        return openBuildingsVersion;
    }

    public Double getPopulationDensity() {
        return populationDensity;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public String getTenantId() {
        return tenantId;
    }
}
