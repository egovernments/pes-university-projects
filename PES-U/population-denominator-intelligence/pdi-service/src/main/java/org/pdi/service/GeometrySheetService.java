package org.pdi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.pdi.config.PdiConfiguration;
import org.pdi.exception.CustomException;
import org.pdi.repository.ArtifactRepository;
import org.pdi.repository.BoundaryGeometryRepository;
import org.pdi.service.enrichment.PopulationRunEnrichmentService;
import org.pdi.util.PythonEngineUtil;
import org.pdi.validator.geometry.BoundaryGeometryValidator;
import org.pdi.web.models.AuditDetails;
import org.pdi.web.models.BoundaryGeometry;
import org.pdi.web.models.BoundaryGeometrySearchCriteria;
import org.pdi.web.models.BoundaryGeometrySearchRequest;
import org.pdi.web.models.BoundaryGeometrySearchResponse;
import org.pdi.web.models.BoundaryNode;
import org.pdi.web.models.BoundaryTree;
import org.pdi.web.models.GeometryParseResult;
import org.pdi.web.models.GeometrySheetCriteria;
import org.pdi.web.models.GeometrySheetRequest;
import org.pdi.web.models.GeometryUploadCommand;
import org.pdi.web.models.GeometryUploadResponse;
import org.pdi.web.models.GeometryWarning;
import org.pdi.web.models.ParsedGeometry;
import org.pdi.web.models.PopulationRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.pdi.util.PdiConstants.ARTIFACT_NOT_FOUND;
import static org.pdi.util.PdiConstants.BOUNDARY_GEOMETRY_BASE_PATH;
import static org.pdi.util.PdiConstants.ENGINE_LOG_FILE;
import static org.pdi.util.PdiConstants.ERROR_SHEET_NOT_FOUND_MSG;
import static org.pdi.util.PdiConstants.ERROR_SHEET_PATH;
import static org.pdi.util.PdiConstants.GEOMETRY_ERRORS_EXTENSION;
import static org.pdi.util.PdiConstants.GEOMETRY_POINT;
import static org.pdi.util.PdiConstants.MODE_DELTA;
import static org.pdi.util.PdiConstants.RUN_IN_PROGRESS;
import static org.pdi.util.PdiConstants.UPLOAD_ERROR;
import static org.pdi.util.PdiServiceUtil.deleteDirQuietly;

/**
 * The PDI geometry sheet: generates the fill-in sheet for a hierarchy, stores the locations
 * an upload provides, and starts a DELTA run when any location changed.
 */
@Slf4j
@Service
public class GeometrySheetService {

    private static final String PATH_SEPARATOR = " / ";

    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-fA-F-]{36}");

    private final HcmBoundaryClient hcmBoundaryClient;

    private final LocalizationClient localizationClient;

    private final BoundaryGeometryRepository geometryRepository;

    private final ArtifactRepository artifactRepository;

    private final GeometryResolver geometryResolver;

    private final PythonEngineUtil pythonEngineUtil;

    private final BoundaryPopulationService populationService;

    private final PopulationRunEnrichmentService enrichmentService;

    private final BoundaryGeometryValidator validator;

    private final ObjectMapper objectMapper;

    private final PdiConfiguration config;

    @Autowired
    public GeometrySheetService(HcmBoundaryClient hcmBoundaryClient, LocalizationClient localizationClient,
                                BoundaryGeometryRepository geometryRepository, ArtifactRepository artifactRepository,
                                GeometryResolver geometryResolver, PythonEngineUtil pythonEngineUtil,
                                BoundaryPopulationService populationService,
                                PopulationRunEnrichmentService enrichmentService, BoundaryGeometryValidator validator,
                                ObjectMapper objectMapper, PdiConfiguration config) {
        this.hcmBoundaryClient = hcmBoundaryClient;
        this.localizationClient = localizationClient;
        this.geometryRepository = geometryRepository;
        this.artifactRepository = artifactRepository;
        this.geometryResolver = geometryResolver;
        this.pythonEngineUtil = pythonEngineUtil;
        this.populationService = populationService;
        this.enrichmentService = enrichmentService;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.config = config;
    }

    /** The fill-in sheet; with {@code onlyMissing} (the default) it lists only codes without a location. */
    public byte[] generate(GeometrySheetRequest request) {
        validator.validateGenerateRequest(request);
        GeometrySheetCriteria criteria = request.getGeometrySheetCriteria();
        boolean onlyMissing = criteria.getOnlyMissing() == null || criteria.getOnlyMissing();

        BoundaryTree tree = hcmBoundaryClient.fetchTree(criteria.getTenantId(), criteria.getHierarchyType(),
                request.getRequestInfo());
        Map<String, String> names = localizationClient.fetchBoundaryNames(criteria.getTenantId(),
                criteria.getHierarchyType(), criteria.getLocale(), request.getRequestInfo());
        Map<String, BoundaryGeometry> located = geometriesByCode(criteria.getTenantId(), criteria.getHierarchyType(),
                !onlyMissing);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (BoundaryNode node : tree.all()) {
            BoundaryGeometry geometry = located.get(node.getCode());
            if (onlyMissing && geometry != null) {
                continue;
            }
            rows.add(sheetRow(tree, node, names, geometry));
        }

        Path work = createWorkDir("pdi-geometry-sheet-");
        try {
            Path rowsJson = work.resolve("rows.json");
            Path sheet = work.resolve("geometry.xlsx");
            objectMapper.writeValue(rowsJson.toFile(), rows);
            pythonEngineUtil.generateGeometrySheet(rowsJson, sheet, work.resolve(ENGINE_LOG_FILE));
            log.info("Geometry sheet for {}/{}: {} of {} boundaries", criteria.getTenantId(),
                    criteria.getHierarchyType(), rows.size(), tree.size());
            return Files.readAllBytes(sheet);
        } catch (IOException e) {
            throw new CustomException(UPLOAD_ERROR, "could not build the geometry sheet",
                    HttpStatus.INTERNAL_SERVER_ERROR, e);
        } finally {
            deleteDirQuietly(work);
        }
    }

    public GeometryUploadResponse upload(MultipartFile sheet, MultipartFile shapes, GeometryUploadCommand command) {
        validator.validateUpload(sheet, shapes, command);
        BoundaryTree tree = hcmBoundaryClient.fetchTree(command.getTenantId(), command.getHierarchyType(),
                command.getRequestInfo());
        List<BoundaryGeometry> stored = geometryRepository.search(command.getTenantId(), command.getHierarchyType(),
                null, true);

        String uploadId = UUID.randomUUID().toString();
        Path work = createWorkDir("pdi-geometry-upload-");
        try {
            Path sheetPath = stage(sheet, work, "sheet");
            Path shapesPath = stage(shapes, work, "shapes");
            Path treeJson = work.resolve("tree.json");
            Path storedPolygons = work.resolve("stored.geojson");
            Path annotated = work.resolve("errors.xlsx");
            objectMapper.writeValue(treeJson.toFile(), treeRows(tree));
            geometryResolver.writePolygons(stored, storedPolygons);

            GeometryParseResult parsed = pythonEngineUtil.parseGeometryUpload(treeJson, command.getIso3(),
                    sheetPath, shapesPath, storedPolygons, annotated, work.resolve(ENGINE_LOG_FILE));
            int changed = geometryRepository.upsert(toGeometries(parsed.getAccepted(), command, uploadId),
                    config.getPopulationBatchSize());
            log.info("Geometry upload {} for {}/{}: {} accepted ({} changed), {} rejected", uploadId,
                    command.getTenantId(), command.getHierarchyType(), parsed.getAccepted().size(), changed,
                    parsed.getRejected().size());

            String errorSheetUrl = null;
            if (sheetPath != null && !parsed.getRejected().isEmpty() && Files.exists(annotated)) {
                String artifactId = artifactRepository.store(annotated, GEOMETRY_ERRORS_EXTENSION);
                errorSheetUrl = BOUNDARY_GEOMETRY_BASE_PATH + artifactId + ERROR_SHEET_PATH;
            }
            List<GeometryWarning> warnings = new ArrayList<>(parsed.getWarnings());
            PopulationRun run = changed > 0 && command.isStartRun() ? startDelta(command, warnings) : null;

            return GeometryUploadResponse.builder()
                    .responseInfo(enrichmentService.responseInfo(command.getRequestInfo()))
                    .uploadId(uploadId)
                    .acceptedCount(parsed.getAccepted().size())
                    .rejectedCount(parsed.getRejected().size())
                    .rejected(parsed.getRejected())
                    .warnings(warnings)
                    .errorSheetUrl(errorSheetUrl)
                    .populationRun(run)
                    .build();
        } catch (IOException e) {
            throw new CustomException(UPLOAD_ERROR, "could not stage the geometry upload",
                    HttpStatus.INTERNAL_SERVER_ERROR, e);
        } finally {
            deleteDirQuietly(work);
        }
    }

    /** Which codes have a location, of which kind, and which are still missing. */
    public BoundaryGeometrySearchResponse search(BoundaryGeometrySearchRequest request) {
        validator.validateSearchRequest(request);
        BoundaryGeometrySearchCriteria criteria = request.getBoundaryGeometrySearchCriteria();
        BoundaryTree tree = hcmBoundaryClient.fetchTree(criteria.getTenantId(), criteria.getHierarchyType(),
                request.getRequestInfo());
        List<BoundaryGeometry> geometries = geometryRepository.search(criteria.getTenantId(),
                criteria.getHierarchyType(), criteria.getBoundaryCodes(), criteria.isIncludeGeometry());
        Set<String> located = geometries.stream().map(BoundaryGeometry::getBoundaryCode).collect(Collectors.toSet());
        List<String> requested = criteria.getBoundaryCodes() == null || criteria.getBoundaryCodes().isEmpty()
                ? new ArrayList<>(tree.getNodes().keySet()) : criteria.getBoundaryCodes();
        List<String> missing = requested.stream().filter(tree::contains).filter(code -> !located.contains(code)).toList();
        return BoundaryGeometrySearchResponse.builder()
                .responseInfo(enrichmentService.responseInfo(request.getRequestInfo()))
                .boundaryGeometry(geometries)
                .missingBoundaryCodes(missing)
                .locatedCount(geometries.size())
                .missingCount(missing.size())
                .build();
    }

    public Path getErrorSheet(String artifactId) {
        if (!UUID_PATTERN.matcher(artifactId).matches()) {
            throw new CustomException(ARTIFACT_NOT_FOUND, ERROR_SHEET_NOT_FOUND_MSG, HttpStatus.NOT_FOUND);
        }
        return artifactRepository.find(artifactId, GEOMETRY_ERRORS_EXTENSION)
                .orElseThrow(() -> new CustomException(ARTIFACT_NOT_FOUND, ERROR_SHEET_NOT_FOUND_MSG,
                        HttpStatus.NOT_FOUND));
    }

    /** Starts a DELTA run, keeping the active run's year and groups unless the upload names its own. */
    private PopulationRun startDelta(GeometryUploadCommand command, List<GeometryWarning> warnings) {
        Optional<PopulationRun> active = populationService.findActiveRun(command.getTenantId(),
                command.getHierarchyType());
        PopulationRun run = PopulationRun.builder()
                .tenantId(command.getTenantId())
                .hierarchyType(command.getHierarchyType())
                .iso3(command.getIso3())
                .year(command.getYear() != null ? command.getYear() : active.map(PopulationRun::getYear).orElse(null))
                .groups(command.getGroups() != null ? command.getGroups()
                        : active.map(PopulationRun::getGroups).orElse(null))
                .mode(MODE_DELTA)
                .activateOnSuccess(true)
                .build();
        try {
            return populationService.submit(run, command.getRequestInfo());
        } catch (CustomException e) {
            if (!RUN_IN_PROGRESS.equals(e.getCode())) {
                throw e;
            }
            warnings.add(GeometryWarning.builder()
                    .boundaryCodes(Collections.emptyList())
                    .reason("locations saved, but a run is already in progress; start a DELTA run once it finishes")
                    .build());
            return null;
        }
    }

    private Map<String, Object> sheetRow(BoundaryTree tree, BoundaryNode node, Map<String, String> names,
                                         BoundaryGeometry geometry) {
        List<String> path = new ArrayList<>();
        for (BoundaryNode ancestor : tree.ancestors(node.getCode())) {
            path.add(0, names.getOrDefault(ancestor.getCode(), ancestor.getCode()));
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("boundaryCode", node.getCode());
        row.put("boundaryType", node.getBoundaryType());
        row.put("level", node.getLevel());
        row.put("name", names.getOrDefault(node.getCode(), node.getCode()));
        row.put("parentPath", String.join(PATH_SEPARATOR, path));
        if (geometry != null && GEOMETRY_POINT.equals(geometry.getGeometryType()) && geometry.getGeometry() != null) {
            JsonNode coordinates = geometry.getGeometry().get("coordinates");
            row.put("longitude", coordinates.get(0).asDouble());
            row.put("latitude", coordinates.get(1).asDouble());
        }
        return row;
    }

    private List<Map<String, Object>> treeRows(BoundaryTree tree) {
        List<Map<String, Object>> rows = new ArrayList<>(tree.size());
        for (BoundaryNode node : tree.all()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("boundaryCode", node.getCode());
            row.put("parentCode", node.getParentCode());
            rows.add(row);
        }
        return rows;
    }

    private List<BoundaryGeometry> toGeometries(List<ParsedGeometry> accepted, GeometryUploadCommand command,
                                                String uploadId) {
        long now = System.currentTimeMillis();
        String user = enrichmentService.userId(command.getRequestInfo());
        List<BoundaryGeometry> geometries = new ArrayList<>(accepted.size());
        for (ParsedGeometry parsed : accepted) {
            geometries.add(BoundaryGeometry.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(command.getTenantId())
                    .hierarchyType(command.getHierarchyType())
                    .boundaryCode(parsed.getBoundaryCode())
                    .geometryType(parsed.getGeometryType())
                    .geometry(parsed.getGeometry())
                    .source(parsed.getSource())
                    .fileStoreId(uploadId)
                    .auditDetails(AuditDetails.builder().createdBy(user).createdTime(now)
                            .lastModifiedBy(user).lastModifiedTime(now).build())
                    .build());
        }
        return geometries;
    }

    private Map<String, BoundaryGeometry> geometriesByCode(String tenantId, String hierarchyType,
                                                           boolean includeGeometry) {
        return geometryRepository.search(tenantId, hierarchyType, null, includeGeometry).stream()
                .collect(Collectors.toMap(BoundaryGeometry::getBoundaryCode, Function.identity()));
    }

    private Path stage(MultipartFile file, Path work, String name) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }
        Path target = work.resolve(name + "." + validator.extension(file));
        file.transferTo(target);
        return target;
    }

    private Path createWorkDir(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException e) {
            throw new CustomException(UPLOAD_ERROR, "could not create a work directory",
                    HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }
}
