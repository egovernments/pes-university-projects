package org.pdi.validator.population;

import org.pdi.config.PdiConfiguration;
import org.pdi.exception.CustomException;
import org.pdi.web.models.BoundaryPopulationSearchCriteria;
import org.pdi.web.models.BoundaryPopulationSearchRequest;
import org.pdi.web.models.PopulationRun;
import org.pdi.web.models.PopulationRunRequest;
import org.pdi.web.models.PopulationRunSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static org.pdi.util.PdiConstants.HIERARCHY_TYPE_REQUIRED;
import static org.pdi.util.PdiConstants.INVALID_GROUP;
import static org.pdi.util.PdiConstants.INVALID_REQUEST;
import static org.pdi.util.PdiConstants.ISO3_REQUIRED;
import static org.pdi.util.PdiConstants.MODE_DELTA;
import static org.pdi.util.PdiConstants.MODE_FULL;
import static org.pdi.util.PdiConstants.TENANT_ID_REQUIRED;
import static org.pdi.util.PdiConstants.TOO_MANY_CODES;

@Component
public class BoundaryPopulationValidator {

    private static final Pattern ISO3 = Pattern.compile("[A-Za-z]{3}");

    private static final Pattern GROUP = Pattern.compile("[a-z0-9_]+");

    private static final Set<String> MODES = Set.of(MODE_DELTA, MODE_FULL);

    private final PdiConfiguration config;

    @Autowired
    public BoundaryPopulationValidator(PdiConfiguration config) {
        this.config = config;
    }

    public void validateComputeRequest(PopulationRunRequest request) {
        if (request == null || request.getPopulationRun() == null) {
            throw new CustomException(INVALID_REQUEST, "PopulationRun is required", HttpStatus.BAD_REQUEST);
        }
        PopulationRun run = request.getPopulationRun();
        validateScope(run.getTenantId(), run.getHierarchyType());
        validateIso3(run.getIso3());
        if (run.getMode() != null && !MODES.contains(run.getMode().toUpperCase(Locale.ROOT))) {
            throw new CustomException(INVALID_REQUEST, "mode must be DELTA or FULL", HttpStatus.BAD_REQUEST);
        }
        if (run.getGroups() != null) {
            run.getGroups().stream()
                    .filter(group -> group == null || !GROUP.matcher(group.strip()).matches())
                    .findFirst()
                    .ifPresent(group -> {
                        throw new CustomException(INVALID_GROUP, "invalid population group: " + group,
                                HttpStatus.BAD_REQUEST);
                    });
        }
    }

    public void validateRunSearchRequest(PopulationRunSearchRequest request) {
        if (request == null || request.getPopulationRunSearchCriteria() == null) {
            throw new CustomException(INVALID_REQUEST, "PopulationRunSearchCriteria is required",
                    HttpStatus.BAD_REQUEST);
        }
        if (isBlank(request.getPopulationRunSearchCriteria().getTenantId())) {
            throw new CustomException(TENANT_ID_REQUIRED, "tenantId is required", HttpStatus.BAD_REQUEST);
        }
    }

    public void validateSearchRequest(BoundaryPopulationSearchRequest request) {
        if (request == null || request.getBoundaryPopulationSearchCriteria() == null) {
            throw new CustomException(INVALID_REQUEST, "BoundaryPopulationSearchCriteria is required",
                    HttpStatus.BAD_REQUEST);
        }
        BoundaryPopulationSearchCriteria criteria = request.getBoundaryPopulationSearchCriteria();
        validateScope(criteria.getTenantId(), criteria.getHierarchyType());
        if (criteria.getBoundaryCodes() == null || criteria.getBoundaryCodes().isEmpty()) {
            throw new CustomException(INVALID_REQUEST, "boundaryCodes is required", HttpStatus.BAD_REQUEST);
        }
        if (criteria.getBoundaryCodes().size() > config.getPopulationSearchMaxCodes()) {
            throw new CustomException(TOO_MANY_CODES, "at most " + config.getPopulationSearchMaxCodes()
                    + " boundary codes per call", HttpStatus.BAD_REQUEST);
        }
    }

    public void validateScope(String tenantId, String hierarchyType) {
        if (isBlank(tenantId)) {
            throw new CustomException(TENANT_ID_REQUIRED, "tenantId is required", HttpStatus.BAD_REQUEST);
        }
        if (isBlank(hierarchyType)) {
            throw new CustomException(HIERARCHY_TYPE_REQUIRED, "hierarchyType is required", HttpStatus.BAD_REQUEST);
        }
    }

    public void validateIso3(String iso3) {
        if (iso3 == null || !ISO3.matcher(iso3).matches()) {
            throw new CustomException(ISO3_REQUIRED, "iso3 must be a three-letter country code",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
