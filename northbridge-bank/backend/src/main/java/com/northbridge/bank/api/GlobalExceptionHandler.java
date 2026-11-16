package com.northbridge.bank.api;

import com.northbridge.bank.web.CorrelationIdFilter;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ErrorMapper mapper;

    public GlobalExceptionHandler(ErrorMapper mapper) {
        this.mapper = mapper;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Void>> api(ApiException ex) {
        return respond(mapper.map(ex));
    }

    /** The envelope itself is malformed (bad operation name, oversize meta, ...). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> envelope(MethodArgumentNotValidException ex) {
        List<ApiError.FieldIssue> issues = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldIssue(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return respond(new ErrorMapper.Mapped(HttpStatus.BAD_REQUEST,
                new ApiError("INVALID_REQUEST", "Malformed request envelope", issues)));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    ResponseEntity<ApiResponse<Void>> unreadable(Exception ex) {
        return respond(new ErrorMapper.Mapped(HttpStatus.BAD_REQUEST,
                new ApiError("INVALID_REQUEST", "Request body must be valid JSON")));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> denied(AccessDeniedException ex) {
        return respond(mapper.map(ApiException.notPermitted("?", ex.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception ex) {
        return respond(mapper.map(ex));
    }

    private ResponseEntity<ApiResponse<Void>> respond(ErrorMapper.Mapped m) {
        String operation = MDC.get("operation");
        return ResponseEntity.status(m.status())
                .body(ApiResponse.fail(operation, m.error(), MDC.get(CorrelationIdFilter.MDC_KEY)));
    }
}
