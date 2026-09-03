package org.pdi.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Read view of a detected invisible settlement. The {@code centroid} and
 * {@code convex_hull} geometry columns are written by the batch engine but not
 * mapped here; the dashboard needs only the counts and addressing attributes.
 */
@Entity
@Table(name = "invisible_settlement")
public class InvisibleSettlement {

    @Id
    private UUID id;

    @Column(name = "cluster_id")
    private String clusterId;

    @Column(name = "building_count")
    private Integer buildingCount;

    @Column(name = "estimated_population")
    private Integer estimatedPopulation;

    @Column(name = "nearest_boundary_code")
    private String nearestBoundaryCode;

    @Column(name = "distance_to_nearest_km")
    private Double distanceToNearestKm;

    @Column(name = "parent_boundary_code")
    private String parentBoundaryCode;

    @Column(name = "status")
    private String status;

    @Column(name = "detected_at")
    private Instant detectedAt;

    @Column(name = "tenant_id")
    private String tenantId;

    public UUID getId() {
        return id;
    }

    public String getClusterId() {
        return clusterId;
    }

    public Integer getBuildingCount() {
        return buildingCount;
    }

    public Integer getEstimatedPopulation() {
        return estimatedPopulation;
    }

    public String getNearestBoundaryCode() {
        return nearestBoundaryCode;
    }

    public Double getDistanceToNearestKm() {
        return distanceToNearestKm;
    }

    public String getParentBoundaryCode() {
        return parentBoundaryCode;
    }

    public String getStatus() {
        return status;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public String getTenantId() {
        return tenantId;
    }
}
