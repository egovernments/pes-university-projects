package org.pdi.persistence.projection;

public interface SummaryProjection {

    long getEstimatedPopulation();

    long getRegisteredPopulation();

    long getEstimatedHouseholds();

    long getRegisteredHouseholds();
}
