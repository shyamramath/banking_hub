package com.northbridge.bank.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(String code, String message, List<FieldIssue> details) {

    public ApiError(String code, String message) {
        this(code, message, List.of());
    }

    public record FieldIssue(String field, String issue) {
    }
}
