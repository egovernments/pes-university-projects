package org.pdi.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Read view of a settlement boundary. The {@code polygon} geometry column is
 * written by the batch engine but intentionally not mapped here; the API serves
 * geometry as GeoJSON artifacts, so the read model needs only the attributes.
 */
@Entity
@Table(name = "settlement_boundary")
public class SettlementBoundary {

    @Id
    private UUID id;

    @Column(name = "boundary_code")
    private String boundaryCode;

    @Column(name = "boundary_type")
    private String boundaryType;

    @Column(name = "name")
    private String name;

    @Column(name = "parent_boundary_code")
    private String parentBoundaryCode;

    @Column(name = "area_km2")
    private Double areaKm2;

    @Column(name = "tenant_id")
    private String tenantId;

    public UUID getId() {
        return id;
    }

    public String getBoundaryCode() {
        return boundaryCode;
    }

    public String getBoundaryType() {
        return boundaryType;
    }

    public String getName() {
        return name;
    }

    public String getParentBoundaryCode() {
        return parentBoundaryCode;
    }

    public Double getAreaKm2() {
        return areaKm2;
    }

    public String getTenantId() {
        return tenantId;
    }
}
