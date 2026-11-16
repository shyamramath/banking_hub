package com.northbridge.bank.dispatch;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.northbridge.bank.api.ApiError;
import com.northbridge.bank.api.ApiException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Binds the untyped payload into the handler's input record, strictly:
 * unknown fields are rejected (stops mass-assignment such as {@code "ownerId": "someone-else"}),
 * then Bean Validation constraints are applied.
 */
@Component
public class PayloadBinder {

    private final ObjectMapper strict;
    private final Validator validator;

    public PayloadBinder(ObjectMapper objectMapper, Validator validator) {
        this.strict = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.validator = validator;
    }

    public <I> I bind(JsonNode payload, Class<I> type) {
        JsonNode node = payload == null || payload.isNull() ? strict.createObjectNode() : payload;
        if (!node.isObject()) {
            throw ApiException.invalidPayload("Payload must be a JSON object", List.of());
        }

        I input;
        try {
            input = strict.treeToValue(node, type);
        } catch (UnrecognizedPropertyException e) {
            throw ApiException.invalidPayload("Unknown field in payload",
                    List.of(new ApiError.FieldIssue(e.getPropertyName(), "is not allowed")));
        } catch (Exception e) {
            throw ApiException.invalidPayload("Payload has the wrong shape", List.of());
        }

        Set<ConstraintViolation<I>> violations = validator.validate(input);
        if (!violations.isEmpty()) {
            List<ApiError.FieldIssue> issues = violations.stream()
                    .map(v -> new ApiError.FieldIssue(v.getPropertyPath().toString(), v.getMessage()))
                    .sorted(Comparator.comparing(ApiError.FieldIssue::field))
                    .toList();
            throw ApiException.invalidPayload("Payload failed validation", issues);
        }
        return input;
    }
}
