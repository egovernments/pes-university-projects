package org.pdi.service;

import lombok.extern.slf4j.Slf4j;
import org.pdi.config.PdiConfiguration;
import org.pdi.exception.CustomException;
import org.pdi.web.models.BoundaryNode;
import org.pdi.web.models.BoundaryTree;
import org.pdi.web.models.RequestInfo;
import org.pdi.web.models.hcm.BoundaryHierarchyDefinition;
import org.pdi.web.models.hcm.BoundaryHierarchyDefinitionResponse;
import org.pdi.web.models.hcm.BoundaryRelationshipResponse;
import org.pdi.web.models.hcm.BoundaryTypeLevel;
import org.pdi.web.models.hcm.EnrichedBoundary;
import org.pdi.web.models.hcm.TenantBoundary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.pdi.util.PdiConstants.BOUNDARY_SERVICE_ERROR;
import static org.pdi.util.PdiConstants.HIERARCHY_NOT_FOUND;

/**
 * Reads a hierarchy's levels, codes and parent links from HCM boundary-service. No geometry
 * is read: boundary-service does not hold any.
 */
@Slf4j
@Service
public class HcmBoundaryClient {

    private final RestTemplate restTemplate;

    private final PdiConfiguration config;

    @Autowired
    public HcmBoundaryClient(RestTemplate restTemplate, PdiConfiguration config) {
        this.restTemplate = restTemplate;
        this.config = config;
    }

    public BoundaryTree fetchTree(String tenantId, String hierarchyType, RequestInfo requestInfo) {
        List<String> levels = fetchLevels(tenantId, hierarchyType, requestInfo);
        BoundaryTree tree = new BoundaryTree(tenantId, hierarchyType, levels);
        for (EnrichedBoundary root : fetchRoots(tenantId, hierarchyType, requestInfo)) {
            addSubtree(tree, root, null, levels);
        }
        if (tree.size() == 0) {
            throw new CustomException(HIERARCHY_NOT_FOUND,
                    "boundary-service has no boundaries for " + hierarchyType + " in " + tenantId,
                    HttpStatus.NOT_FOUND);
        }
        log.info("Fetched {} boundaries over {} levels for {}/{}", tree.size(), levels.size(), tenantId, hierarchyType);
        return tree;
    }

    private List<String> fetchLevels(String tenantId, String hierarchyType, RequestInfo requestInfo) {
        Map<String, Object> criteria = new HashMap<>();
        criteria.put("tenantId", tenantId);
        criteria.put("hierarchyType", hierarchyType);
        criteria.put("limit", 1);
        criteria.put("offset", 0);
        Map<String, Object> body = new HashMap<>();
        body.put("RequestInfo", requestInfo);
        body.put("BoundaryTypeHierarchySearchCriteria", criteria);

        BoundaryHierarchyDefinitionResponse response = post(
                config.getBoundaryHost() + config.getHierarchySearchPath(), body,
                BoundaryHierarchyDefinitionResponse.class);
        if (response == null || response.getBoundaryHierarchy() == null || response.getBoundaryHierarchy().isEmpty()) {
            throw new CustomException(HIERARCHY_NOT_FOUND,
                    "hierarchy type " + hierarchyType + " is not defined for " + tenantId, HttpStatus.NOT_FOUND);
        }
        BoundaryHierarchyDefinition definition = response.getBoundaryHierarchy().get(0);
        return orderLevels(definition.getBoundaryHierarchy());
    }

    /** Boundary types from the top level down, following each type's parent link. */
    private List<String> orderLevels(List<BoundaryTypeLevel> types) {
        Map<String, String> childOf = new HashMap<>();
        String top = null;
        for (BoundaryTypeLevel type : types) {
            if (type.getParentBoundaryType() == null) {
                top = type.getBoundaryType();
            } else {
                childOf.put(type.getParentBoundaryType(), type.getBoundaryType());
            }
        }
        List<String> levels = new ArrayList<>();
        for (String type = top; type != null && !levels.contains(type); type = childOf.get(type)) {
            levels.add(type);
        }
        return levels;
    }

    private List<EnrichedBoundary> fetchRoots(String tenantId, String hierarchyType, RequestInfo requestInfo) {
        String url = UriComponentsBuilder
                .fromUriString(config.getBoundaryHost() + config.getRelationshipSearchPath())
                .queryParam("includeChildren", true)
                .queryParam("tenantId", tenantId)
                .queryParam("hierarchyType", hierarchyType)
                .toUriString();
        Map<String, Object> body = new HashMap<>();
        body.put("RequestInfo", requestInfo);

        BoundaryRelationshipResponse response = post(url, body, BoundaryRelationshipResponse.class);
        List<EnrichedBoundary> roots = new ArrayList<>();
        if (response != null && response.getTenantBoundary() != null) {
            for (TenantBoundary tenantBoundary : response.getTenantBoundary()) {
                if (tenantBoundary.getBoundary() != null) {
                    roots.addAll(tenantBoundary.getBoundary());
                }
            }
        }
        return roots;
    }

    private void addSubtree(BoundaryTree tree, EnrichedBoundary boundary, String parentCode, List<String> levels) {
        List<String> childCodes = new ArrayList<>();
        if (boundary.getChildren() != null) {
            boundary.getChildren().forEach(child -> childCodes.add(child.getCode()));
        }
        int level = levels.indexOf(boundary.getBoundaryType()) + 1;
        tree.add(BoundaryNode.builder()
                .code(boundary.getCode())
                .boundaryType(boundary.getBoundaryType())
                .parentCode(parentCode)
                .level(level > 0 ? level : levels.size() + 1)
                .childCodes(childCodes)
                .build());
        if (boundary.getChildren() != null) {
            for (EnrichedBoundary child : boundary.getChildren()) {
                addSubtree(tree, child, boundary.getCode(), levels);
            }
        }
    }

    private <T> T post(String url, Object body, Class<T> responseType) {
        try {
            return restTemplate.postForObject(url, body, responseType);
        } catch (RestClientException e) {
            throw new CustomException(BOUNDARY_SERVICE_ERROR, "boundary-service call failed: " + e.getMessage(),
                    HttpStatus.BAD_GATEWAY, e);
        }
    }
}
