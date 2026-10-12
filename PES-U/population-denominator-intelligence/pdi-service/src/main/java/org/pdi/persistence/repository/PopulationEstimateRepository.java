package org.pdi.persistence.repository;

import org.pdi.persistence.entity.PopulationEstimate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PopulationEstimateRepository extends JpaRepository<PopulationEstimate, UUID> {

    Optional<PopulationEstimate> findByBoundaryCodeAndTenantId(String boundaryCode, String tenantId);
}
