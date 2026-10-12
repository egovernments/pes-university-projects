package org.pdi.persistence.projection;

public interface TopGapProjection {

    String getName();

    String getBoundaryCode();

    long getGap();

    Double getCoverageRatio();

    Integer getRiskScore();

    String getRiskPriority();
}
