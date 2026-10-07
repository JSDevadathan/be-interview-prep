package com.edstem.interviewprep.common.error;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String FIELD_ERRORS_PROPERTY = "fieldErrors";
    private static final String VALIDATION_FAILED_DETAIL = "Request validation failed";

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<FieldErrorDetail> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldErrorDetail::from)
                .sorted(Comparator.comparing(FieldErrorDetail::field))
                .toList();
        return handleExceptionInternal(exception, validationProblem(status, fieldErrors), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        if (!(exception.getCause() instanceof MismatchedInputException mismatch) || mismatch.getPath().isEmpty()) {
            return super.handleHttpMessageNotReadable(exception, headers, status, request);
        }
        List<FieldErrorDetail> fieldErrors = List.of(FieldErrorDetail.from(mismatch));
        return handleExceptionInternal(exception, validationProblem(status, fieldErrors), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        if (!(exception instanceof MethodArgumentTypeMismatchException mismatch)) {
            return super.handleTypeMismatch(exception, headers, status, request);
        }
        List<FieldErrorDetail> fieldErrors =
                List.of(FieldErrorDetail.forInvalidValue(mismatch.getName(), mismatch.getRequiredType()));
        return handleExceptionInternal(exception, validationProblem(status, fieldErrors), headers, status, request);
    }

    @ExceptionHandler(FieldValidationException.class)
    ProblemDetail handleFieldValidation(FieldValidationException exception) {
        return validationProblem(HttpStatus.BAD_REQUEST, List.of(FieldErrorDetail.from(exception)));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleResourceNotFound(ResourceNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ResourceGoneException.class)
    ProblemDetail handleResourceGone(ResourceGoneException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.GONE, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unhandled exception while processing request", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private static ProblemDetail validationProblem(HttpStatusCode status, List<FieldErrorDetail> fieldErrors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, VALIDATION_FAILED_DETAIL);
        problem.setProperty(FIELD_ERRORS_PROPERTY, fieldErrors);
        return problem;
    }
}
