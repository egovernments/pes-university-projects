package org.pdi.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    // The UI reads the problem's "detail" field and shows it to the user as-is.
    @ExceptionHandler(CustomException.class)
    public ProblemDetail handleCustomException(CustomException exception) {
        log.error("Request failed with {}: {}", exception.getCode(), exception.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        problem.setProperty("code", exception.getCode());
        return problem;
    }
}
