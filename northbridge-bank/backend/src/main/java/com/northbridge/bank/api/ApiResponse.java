package com.northbridge.bank.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String operation, boolean success, T data, ApiError error, String correlationId) {

    public static <T> ApiResponse<T> ok(String operation, T data, String correlationId) {
        return new ApiResponse<>(operation, true, data, null, correlationId);
    }

    public static <T> ApiResponse<T> fail(String operation, ApiError error, String correlationId) {
        return new ApiResponse<>(operation, false, null, error, correlationId);
    }
}
