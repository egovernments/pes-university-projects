package org.pdi.validator.target;

import lombok.extern.slf4j.Slf4j;
import org.pdi.exception.CustomException;
import org.pdi.util.Uploads;
import org.pdi.web.models.EngineInput;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import static org.pdi.util.PdiConstants.INVALID_FILE_TYPE;
import static org.pdi.util.PdiConstants.ISO3_REQUIRED;
import static org.pdi.util.PdiConstants.ISO3_REQUIRED_MSG;

@Slf4j
@Component
public class TargetValidator {

    /**
     * Validates a compute request: every uploaded file must carry the extension its input
     * expects, and a country must be named.
     */
    public void validateComputeRequest(Map<EngineInput, MultipartFile> files, String iso3) {
        log.info("Validating compute request for {}", iso3);
        files.forEach(this::validateFileExtension);
        if (iso3 == null || iso3.isBlank()) {
            throw new CustomException(ISO3_REQUIRED, ISO3_REQUIRED_MSG, HttpStatus.BAD_REQUEST);
        }
    }

    private void validateFileExtension(EngineInput input, MultipartFile file) {
        if (!Uploads.hasExpectedExtension(input, file)) {
            throw new CustomException(INVALID_FILE_TYPE,
                    input.getParameter() + " must be a " + input.getExtension() + " file, got "
                            + file.getOriginalFilename(),
                    HttpStatus.BAD_REQUEST);
        }
    }
}
