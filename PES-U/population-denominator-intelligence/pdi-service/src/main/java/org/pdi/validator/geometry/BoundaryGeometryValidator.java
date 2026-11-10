package org.pdi.validator.geometry;

import org.pdi.exception.CustomException;
import org.pdi.validator.population.BoundaryPopulationValidator;
import org.pdi.web.models.BoundaryGeometrySearchRequest;
import org.pdi.web.models.GeometrySheetRequest;
import org.pdi.web.models.GeometryUploadCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

import static org.pdi.util.PdiConstants.INVALID_FILE_TYPE;
import static org.pdi.util.PdiConstants.INVALID_REQUEST;

@Component
public class BoundaryGeometryValidator {

    private static final Set<String> SHAPE_EXTENSIONS = Set.of("geojson", "json", "zip");

    private final BoundaryPopulationValidator populationValidator;

    @Autowired
    public BoundaryGeometryValidator(BoundaryPopulationValidator populationValidator) {
        this.populationValidator = populationValidator;
    }

    public void validateGenerateRequest(GeometrySheetRequest request) {
        if (request == null || request.getGeometrySheetCriteria() == null) {
            throw new CustomException(INVALID_REQUEST, "GeometrySheetCriteria is required", HttpStatus.BAD_REQUEST);
        }
        populationValidator.validateScope(request.getGeometrySheetCriteria().getTenantId(),
                request.getGeometrySheetCriteria().getHierarchyType());
    }

    public void validateSearchRequest(BoundaryGeometrySearchRequest request) {
        if (request == null || request.getBoundaryGeometrySearchCriteria() == null) {
            throw new CustomException(INVALID_REQUEST, "BoundaryGeometrySearchCriteria is required",
                    HttpStatus.BAD_REQUEST);
        }
        populationValidator.validateScope(request.getBoundaryGeometrySearchCriteria().getTenantId(),
                request.getBoundaryGeometrySearchCriteria().getHierarchyType());
    }

    public void validateUpload(MultipartFile sheet, MultipartFile shapes, GeometryUploadCommand command) {
        populationValidator.validateScope(command.getTenantId(), command.getHierarchyType());
        populationValidator.validateIso3(command.getIso3());
        boolean hasSheet = sheet != null && !sheet.isEmpty();
        boolean hasShapes = shapes != null && !shapes.isEmpty();
        if (!hasSheet && !hasShapes) {
            throw new CustomException(INVALID_REQUEST, "upload a filled geometry sheet, a shape file, or both",
                    HttpStatus.BAD_REQUEST);
        }
        if (hasSheet && !"xlsx".equals(extension(sheet))) {
            throw new CustomException(INVALID_FILE_TYPE, "the geometry sheet must be an .xlsx file",
                    HttpStatus.BAD_REQUEST);
        }
        if (hasShapes && !SHAPE_EXTENSIONS.contains(extension(shapes))) {
            throw new CustomException(INVALID_FILE_TYPE, "the shape file must be .geojson, .json or a zipped shapefile",
                    HttpStatus.BAD_REQUEST);
        }
    }

    public String extension(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
