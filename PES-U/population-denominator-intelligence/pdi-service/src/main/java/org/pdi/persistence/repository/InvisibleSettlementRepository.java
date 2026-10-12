package org.pdi.persistence.repository;

import org.pdi.persistence.entity.InvisibleSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InvisibleSettlementRepository extends JpaRepository<InvisibleSettlement, UUID> {

    long countByTenantId(String tenantId);

    @Query("SELECT COALESCE(SUM(i.estimatedPopulation), 0) FROM InvisibleSettlement i WHERE i.tenantId = :tenantId")
    long sumEstimatedPopulation(@Param("tenantId") String tenantId);

    List<InvisibleSettlement> findByTenantIdOrderByBuildingCountDesc(String tenantId);
}
