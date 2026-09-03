package org.pdi.web.dto;

import java.util.Map;

public record BoundaryTarget(String boundaryCode, Map<String, Long> values) {
}
