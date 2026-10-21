package org.pdi.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Service error carrying a stable error code, in the shape of the DIGIT tracer
 * {@code CustomException(code, message)}. The status is the HTTP status the API answers with.
 */
@Getter
public class CustomException extends RuntimeException {

    private final String code;

    private final HttpStatus status;

    public CustomException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public CustomException(String code, String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
    }
}
