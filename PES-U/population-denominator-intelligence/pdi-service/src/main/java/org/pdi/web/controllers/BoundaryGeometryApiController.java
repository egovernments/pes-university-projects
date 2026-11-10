package org.pdi.web.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.pdi.exception.CustomException;
import org.pdi.service.GeometrySheetService;
import org.pdi.web.models.BoundaryGeometrySearchRequest;
import org.pdi.web.models.BoundaryGeometrySearchResponse;
import org.pdi.web.models.GeometrySheetRequest;
import org.pdi.web.models.GeometryUploadCommand;
import org.pdi.web.models.GeometryUploadResponse;
import org.pdi.web.models.RequestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

import static org.pdi.util.PdiConstants.INVALID_REQUEST;
import static org.pdi.util.PdiConstants.XLSX_MEDIA_TYPE;

@Controller
@RequestMapping("/population/v1/boundary-geometry")
@Validated
public class BoundaryGeometryApiController {

    private final GeometrySheetService geometrySheetService;

    private final ObjectMapper objectMapper;

    @Autowired
    public BoundaryGeometryApiController(GeometrySheetService geometrySheetService, ObjectMapper objectMapper) {
        this.geometrySheetService = geometrySheetService;
        this.objectMapper = objectMapper;
    }

    /** Download the geometry sheet for a tenant and hierarchy type. */
    @RequestMapping(value = "/_generate", method = RequestMethod.POST)
    public ResponseEntity<byte[]> boundaryGeometryV1GeneratePost(@RequestBody GeometrySheetRequest request) {
        byte[] sheet = geometrySheetService.generate(request);
        String name = request.getGeometrySheetCriteria().getHierarchyType().toLowerCase();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"boundary-geometry-" + name + ".xlsx\"")
                .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
                .body(sheet);
    }

    /**
     * Upload a filled geometry sheet and/or a shape file. {@code requestInfo} is the DIGIT
     * RequestInfo as JSON, passed on to boundary-service.
     */
    @RequestMapping(value = "/_upload", method = RequestMethod.POST, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GeometryUploadResponse> boundaryGeometryV1UploadPost(@RequestParam(value = "sheet", required = false) MultipartFile sheet,
                                                                               @RequestParam(value = "shapes", required = false) MultipartFile shapes,
                                                                               @RequestParam("tenantId") String tenantId,
                                                                               @RequestParam("hierarchyType") String hierarchyType,
                                                                               @RequestParam("iso3") String iso3,
                                                                               @RequestParam(value = "year", required = false) Integer year,
                                                                               @RequestParam(value = "groups", required = false) String groups,
                                                                               @RequestParam(value = "startRun", defaultValue = "true") boolean startRun,
                                                                               @RequestParam(value = "requestInfo", required = false) String requestInfo) {
        GeometryUploadCommand command = GeometryUploadCommand.builder()
                .tenantId(tenantId)
                .hierarchyType(hierarchyType)
                .iso3(iso3)
                .year(year)
                .groups(splitGroups(groups))
                .startRun(startRun)
                .requestInfo(readRequestInfo(requestInfo))
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(geometrySheetService.upload(sheet, shapes, command));
    }

    /** The upload's sheet with an error column naming each rejected row's reason. */
    @RequestMapping(value = "/{uploadId}/_errors", method = RequestMethod.GET)
    public ResponseEntity<Resource> boundaryGeometryV1ErrorsGet(@PathVariable String uploadId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"geometry-errors-" + uploadId + ".xlsx\"")
                .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
                .body(new PathResource(geometrySheetService.getErrorSheet(uploadId)));
    }

    @RequestMapping(value = "/_search", method = RequestMethod.POST)
    public ResponseEntity<BoundaryGeometrySearchResponse> boundaryGeometryV1SearchPost(@RequestBody BoundaryGeometrySearchRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(geometrySheetService.search(request));
    }

    private List<String> splitGroups(String groups) {
        if (groups == null || groups.isBlank()) {
            return null;
        }
        return Arrays.stream(groups.split(",")).map(String::strip).filter(group -> !group.isEmpty()).toList();
    }

    private RequestInfo readRequestInfo(String json) {
        if (json == null || json.isBlank()) {
            return new RequestInfo();
        }
        try {
            return objectMapper.readValue(json, RequestInfo.class);
        } catch (JsonProcessingException e) {
            throw new CustomException(INVALID_REQUEST, "requestInfo is not valid JSON", HttpStatus.BAD_REQUEST, e);
        }
    }
}
