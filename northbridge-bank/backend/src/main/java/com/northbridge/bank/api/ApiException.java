package com.northbridge.bank.api;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Base for all expected failures. {@link #getMessage()} is safe to show to clients;
 * {@link #internalReason()} is for logs only.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String internalReason;
    private final List<ApiError.FieldIssue> details;

    public ApiException(HttpStatus status, String code, String publicMessage, String internalReason,
                        List<ApiError.FieldIssue> details) {
        super(publicMessage);
        this.status = status;
        this.code = code;
        this.internalReason = internalReason;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public ApiException(HttpStatus status, String code, String publicMessage) {
        this(status, code, publicMessage, publicMessage, List.of());
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String internalReason() { return internalReason; }
    public List<ApiError.FieldIssue> details() { return details; }

    public ApiError toApiError() {
        return new ApiError(code, getMessage(), details);
    }

    // ---- Concrete failure types -------------------------------------------------------------

    /**
     * Unknown operation AND missing permission produce the same response on purpose,
     * so callers cannot enumerate which operations exist.
     */
    public static ApiException notPermitted(String operation, String internalReason) {
        return new ApiException(HttpStatus.FORBIDDEN, "OPERATION_NOT_PERMITTED",
                "You are not permitted to perform this operation", operation + ": " + internalReason, null);
    }

    /** Used for both "does not exist" and "belongs to someone else" (prevents BOLA probing). */
    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " not found");
    }

    public static ApiException invalidPayload(String message, List<ApiError.FieldIssue> details) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYLOAD", message, message, details);
    }

    public static ApiException businessRule(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public static ApiException rateLimited(String operation) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                "Too many requests, please slow down", "rate limit hit for " + operation, null);
    }
}
