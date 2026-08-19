package org.pdi.web.dto;

import java.util.List;
import java.util.Map;

public record TargetComputeResponse(String jobId,
                                    String iso3,
                                    int boundaryCount,
                                    List<String> groups,
                                    List<BoundaryTarget> targets,
                                    boolean registeredAvailable,
                                    List<BoundaryTarget> registered,
                                    boolean invisibleAvailable,
                                    int invisibleCount,
                                    boolean catchmentsAvailable,
                                    int catchmentCount,
                                    boolean riskAvailable,
                                    String downloadUrl,
                                    String geojsonUrl,
                                    String settlementsUrl,
                                    String catchmentsUrl,
                                    String buildingsUrl,
                                    String statsUrl,
                                    Map<String, Object> provenance) {
}
