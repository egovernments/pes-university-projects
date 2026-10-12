package org.pdi.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EngineResult(String iso3,
                           int count,
                           List<String> groups,
                           String sheet,
                           String geojson,
                           List<Map<String, Object>> targets,
                           boolean registeredAvailable,
                           List<Map<String, Object>> registered,
                           boolean invisibleAvailable,
                           int invisibleCount,
                           String invisibleGeojson,
                           boolean catchmentsAvailable,
                           int catchmentCount,
                           String catchmentsGeojson,
                           String buildingsGeojson,
                           boolean riskAvailable,
                           String statsJson,
                           /** How the uploads resolved: name matches, misses, cells needing review. */
                           Map<String, Object> provenance) {
}
