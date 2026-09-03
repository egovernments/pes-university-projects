package org.pdi.persistence.repository;

import org.pdi.persistence.entity.SettlementBoundary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SettlementBoundaryRepository extends JpaRepository<SettlementBoundary, UUID> {

    Optional<SettlementBoundary> findByBoundaryCodeAndTenantId(String boundaryCode, String tenantId);
}
