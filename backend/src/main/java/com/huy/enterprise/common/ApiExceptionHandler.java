package com.huy.enterprise.common;

import com.huy.enterprise.ai.*;
import com.huy.enterprise.auth.LoginRateLimitException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(LoginRateLimitException.class)
    ResponseEntity<ApiErrorResponse> rateLimited(LoginRateLimitException ex, HttpServletRequest request) {
        var result = build(HttpStatus.TOO_MANY_REQUESTS, "AUTH_RATE_LIMITED", ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(ex.getRetryAfterSeconds())).body(result.getBody());
    }
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiErrorResponse> notFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(AiServiceInputException.class)
    ResponseEntity<ApiErrorResponse> aiInput(AiServiceInputException ex, HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(AiServiceUnavailableException.class)
    ResponseEntity<ApiErrorResponse> aiUnavailable(AiServiceUnavailableException ex, HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(AiServiceTimeoutException.class)
    ResponseEntity<ApiErrorResponse> aiTimeout(AiServiceTimeoutException ex, HttpServletRequest request) {
        return build(HttpStatus.GATEWAY_TIMEOUT, "AI_TIMEOUT", ex.getMessage(), request);
    }

    @ExceptionHandler(AiServiceProtocolException.class)
    ResponseEntity<ApiErrorResponse> aiProtocol(AiServiceProtocolException ex, HttpServletRequest request) {
        log.warn("Invalid AI response requestId={} path={}", RequestCorrelationFilter.requestId(request), request.getRequestURI());
        return build(HttpStatus.BAD_GATEWAY, "AI_INVALID_RESPONSE", ex.getMessage(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiErrorResponse> unauthorized(AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid username or password", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> forbidden(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action", request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, HandlerMethodValidationException.class, ConstraintViolationException.class})
    ResponseEntity<ApiErrorResponse> malformed(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Malformed JSON, invalid value, or missing request parameter", request);
    }

    @ExceptionHandler({BusinessException.class, IllegalArgumentException.class})
    ResponseEntity<ApiErrorResponse> badRequest(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage()).distinct().collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message.isBlank() ? "Invalid request data" : message, request);
    }

    @ExceptionHandler({ConflictException.class, DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    ResponseEntity<ApiErrorResponse> conflict(Exception ex, HttpServletRequest request) {
        String code = ex instanceof OptimisticLockingFailureException ? "CONCURRENT_UPDATE" : "CONFLICT";
        String message = ex instanceof ConflictException ? ex.getMessage()
                : ex instanceof OptimisticLockingFailureException ? "Data changed concurrently; reload and retry"
                : "Request conflicts with existing data";
        return build(HttpStatus.CONFLICT, code, message, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected API failure requestId={} method={} path={}",
                RequestCorrelationFilter.requestId(request), request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error", request);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(OffsetDateTime.now(), status.value(),
                status.getReasonPhrase(), message, request.getRequestURI(), code, RequestCorrelationFilter.requestId(request)));
    }
}
