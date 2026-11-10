package org.pdi.service;

import lombok.extern.slf4j.Slf4j;
import org.pdi.config.PdiConfiguration;
import org.pdi.web.models.RequestInfo;
import org.pdi.web.models.hcm.LocalizationMessage;
import org.pdi.web.models.hcm.LocalizationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.pdi.util.PdiConstants.BOUNDARY_LOCALIZATION_MODULE_PREFIX;

/**
 * Boundary names from HCM localization, keyed by boundary code. Names only label the geometry
 * sheet, so a failed lookup returns no names rather than failing the request.
 */
@Slf4j
@Service
public class LocalizationClient {

    private final RestTemplate restTemplate;

    private final PdiConfiguration config;

    @Autowired
    public LocalizationClient(RestTemplate restTemplate, PdiConfiguration config) {
        this.restTemplate = restTemplate;
        this.config = config;
    }

    public Map<String, String> fetchBoundaryNames(String tenantId, String hierarchyType, String locale,
                                                  RequestInfo requestInfo) {
        String url = UriComponentsBuilder
                .fromUriString(config.getLocalizationHost() + config.getLocalizationSearchPath())
                .queryParam("tenantId", tenantId)
                .queryParam("module", BOUNDARY_LOCALIZATION_MODULE_PREFIX + hierarchyType.toLowerCase())
                .queryParam("locale", locale == null ? config.getDefaultLocale() : locale)
                .toUriString();
        Map<String, Object> body = new HashMap<>();
        body.put("RequestInfo", requestInfo);
        try {
            LocalizationResponse response = restTemplate.postForObject(url, body, LocalizationResponse.class);
            if (response == null || response.getMessages() == null) {
                return Collections.emptyMap();
            }
            Map<String, String> names = new HashMap<>();
            for (LocalizationMessage message : response.getMessages()) {
                names.put(message.getCode(), message.getMessage());
            }
            return names;
        } catch (RestClientException e) {
            log.warn("Boundary names unavailable for {}/{}: {}", tenantId, hierarchyType, e.getMessage());
            return Collections.emptyMap();
        }
    }
}
