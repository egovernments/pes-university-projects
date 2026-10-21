package org.pdi.service.enrichment;

import lombok.extern.slf4j.Slf4j;
import org.pdi.web.models.BoundaryTarget;
import org.pdi.web.models.ComputeCommand;
import org.pdi.web.models.EngineResult;
import org.pdi.web.models.TargetComputeResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.pdi.util.PdiConstants.BOUNDARY_CODE_KEY;
import static org.pdi.util.PdiConstants.BUILDINGS_PATH;
import static org.pdi.util.PdiConstants.CAMPAIGN_ID_PREFIX;
import static org.pdi.util.PdiConstants.CATCHMENTS_PATH;
import static org.pdi.util.PdiConstants.DEFAULT_CAMPAIGN_YEAR;
import static org.pdi.util.PdiConstants.DOWNLOAD_PATH;
import static org.pdi.util.PdiConstants.GEOJSON_PATH;
import static org.pdi.util.PdiConstants.SETTLEMENTS_PATH;
import static org.pdi.util.PdiConstants.STATS_PATH;
import static org.pdi.util.PdiConstants.TARGETS_BASE_PATH;

@Slf4j
@Component
public class TargetEnrichmentService {

    /**
     * Enriches a validated compute command: the country code is normalised and, when the
     * caller names no campaign, one is derived from the country and year.
     */
    public void enrichComputeRequest(ComputeCommand command) {
        command.setIso3(command.getIso3().trim().toUpperCase());
        command.setCampaignId(resolveCampaignId(command.getCampaignId(), command.getIso3(), command.getYear()));
        log.info("Enriched compute request for {} as campaign {}", command.getIso3(), command.getCampaignId());
    }

    /** Builds the response for a fresh engine run, linking each artifact that was produced. */
    public TargetComputeResponse enrichComputeResponse(String artifactId, EngineResult result,
                                                       boolean sheet, boolean geojson, boolean settlements,
                                                       boolean catchments, boolean buildings, boolean stats) {
        TargetComputeResponse response = TargetComputeResponse.builder()
                .iso3(result.getIso3())
                .boundaryCount(result.getCount())
                .groups(result.getGroups())
                .targets(toTargets(result.getTargets()))
                .registeredAvailable(result.isRegisteredAvailable())
                .registered(toTargets(result.getRegistered()))
                .invisibleAvailable(result.isInvisibleAvailable())
                .invisibleCount(result.getInvisibleCount())
                .catchmentsAvailable(result.isCatchmentsAvailable())
                .catchmentCount(result.getCatchmentCount())
                .riskAvailable(result.isRiskAvailable())
                .provenance(result.getProvenance())
                .build();
        enrichArtifactLinks(response, artifactId, sheet, geojson, settlements, catchments, buildings, stats);
        return response;
    }

    /**
     * Re-mints a cached response against the artifact id its files were restored under.
     * Only the links change; every figure is served exactly as stored.
     */
    public TargetComputeResponse enrichCachedResponse(String artifactId, TargetComputeResponse stored,
                                                      boolean sheet, boolean geojson, boolean settlements,
                                                      boolean catchments, boolean buildings, boolean stats) {
        enrichArtifactLinks(stored, artifactId, sheet, geojson, settlements, catchments, buildings, stats);
        return stored;
    }

    private void enrichArtifactLinks(TargetComputeResponse response, String artifactId,
                                     boolean sheet, boolean geojson, boolean settlements,
                                     boolean catchments, boolean buildings, boolean stats) {
        String base = TARGETS_BASE_PATH + artifactId;
        response.setJobId(artifactId);
        response.setDownloadUrl(sheet ? base + DOWNLOAD_PATH : null);
        response.setGeojsonUrl(geojson ? base + GEOJSON_PATH : null);
        response.setSettlementsUrl(settlements ? base + SETTLEMENTS_PATH : null);
        response.setCatchmentsUrl(catchments ? base + CATCHMENTS_PATH : null);
        response.setBuildingsUrl(buildings ? base + BUILDINGS_PATH : null);
        response.setStatsUrl(stats ? base + STATS_PATH : null);
    }

    private String resolveCampaignId(String campaignId, String iso3, Integer year) {
        if (campaignId != null && !campaignId.isBlank()) {
            return campaignId.trim();
        }
        return CAMPAIGN_ID_PREFIX + iso3 + "-" + (year != null ? year : DEFAULT_CAMPAIGN_YEAR);
    }

    private List<BoundaryTarget> toTargets(List<Map<String, Object>> records) {
        return records.stream().map(this::toTarget).toList();
    }

    private BoundaryTarget toTarget(Map<String, Object> record) {
        Map<String, Long> values = new LinkedHashMap<>();
        String boundaryCode = null;
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            if (BOUNDARY_CODE_KEY.equals(entry.getKey())) {
                boundaryCode = String.valueOf(entry.getValue());
            } else if (entry.getValue() instanceof Number number) {
                values.put(entry.getKey(), number.longValue());
            }
        }
        return BoundaryTarget.builder().boundaryCode(boundaryCode).values(values).build();
    }
}
