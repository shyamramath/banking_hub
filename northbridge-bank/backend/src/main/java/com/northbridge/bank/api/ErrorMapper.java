package com.northbridge.bank.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Converts any exception into a status + client-safe error. Shared by the global exception
 * handler and by the batch endpoint (which must turn per-item failures into per-item results).
 */
@Component
public class ErrorMapper {

    private static final Logger log = LoggerFactory.getLogger(ErrorMapper.class);

    public record Mapped(HttpStatus status, ApiError error) {
    }

    public Mapped map(Throwable ex) {
        if (ex instanceof ApiException api) {
            if (api.status().is4xxClientError()) {
                log.info("Request rejected: {} ({})", api.code(), api.internalReason());
            }
            return new Mapped(api.status(), api.toApiError());
        }
        // Unexpected: log everything server-side, reveal nothing to the client.
        log.error("Unhandled error", ex);
        return new Mapped(HttpStatus.INTERNAL_SERVER_ERROR,
                new ApiError("INTERNAL_ERROR", "Something went wrong. Please try again later."));
    }
}
