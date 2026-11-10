package org.pdi.service.enrichment;

import org.pdi.config.PdiConfiguration;
import org.pdi.web.models.AuditDetails;
import org.pdi.web.models.PopulationRun;
import org.pdi.web.models.RequestInfo;
import org.pdi.web.models.ResponseInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.pdi.util.PdiConstants.MODE_DELTA;
import static org.pdi.util.PdiConstants.RESPONSE_STATUS_SUCCESSFUL;
import static org.pdi.util.PdiConstants.STATUS_PENDING;
import static org.pdi.util.PdiConstants.TOTAL_GROUP;

@Service
public class PopulationRunEnrichmentService {

    private final PdiConfiguration config;

    @Autowired
    public PopulationRunEnrichmentService(PdiConfiguration config) {
        this.config = config;
    }

    /**
     * Fills a new run's id, defaults and audit details. {@code total} is always computed, since
     * it is the figure the boundary sheet reads.
     */
    public void enrichComputeRequest(PopulationRun run, RequestInfo requestInfo) {
        long now = System.currentTimeMillis();
        String user = userId(requestInfo);
        run.setId(UUID.randomUUID().toString());
        run.setIso3(run.getIso3().toUpperCase(Locale.ROOT));
        run.setYear(run.getYear() == null ? config.getPopulationDefaultYear() : run.getYear());
        run.setMode(run.getMode() == null ? MODE_DELTA : run.getMode().toUpperCase(Locale.ROOT));
        run.setActivateOnSuccess(run.getActivateOnSuccess() == null || run.getActivateOnSuccess());
        run.setGroups(normaliseGroups(run.getGroups()));
        run.setStatus(STATUS_PENDING);
        run.setIsActive(false);
        run.setProgress(0);
        run.setAuditDetails(AuditDetails.builder()
                .createdBy(user).createdTime(now)
                .lastModifiedBy(user).lastModifiedTime(now)
                .build());
    }

    public ResponseInfo responseInfo(RequestInfo requestInfo) {
        return ResponseInfo.builder()
                .apiId(requestInfo == null ? null : requestInfo.getApiId())
                .ver(requestInfo == null ? null : requestInfo.getVer())
                .msgId(requestInfo == null ? null : requestInfo.getMsgId())
                .ts(System.currentTimeMillis())
                .status(RESPONSE_STATUS_SUCCESSFUL)
                .build();
    }

    public String userId(RequestInfo requestInfo) {
        return requestInfo == null || requestInfo.getUserInfo() == null ? null : requestInfo.getUserInfo().getUuid();
    }

    private List<String> normaliseGroups(List<String> groups) {
        LinkedHashSet<String> normalised = new LinkedHashSet<>();
        normalised.add(TOTAL_GROUP);
        if (groups != null) {
            groups.stream().filter(group -> group != null && !group.isBlank()).map(String::strip)
                    .forEach(normalised::add);
        }
        return new ArrayList<>(normalised);
    }
}
