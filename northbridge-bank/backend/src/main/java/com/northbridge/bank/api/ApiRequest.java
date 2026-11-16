package com.northbridge.bank.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * The single request envelope.
 *
 * @param operation logical operation name, e.g. {@code accounts.list}
 * @param payload   operation-specific input, bound to the handler's typed input class
 * @param meta      non-business metadata (channel, client version). Never used for authz.
 */
public record ApiRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[a-z]+(\\.[a-z]+)*") String operation,
        JsonNode payload,
        @Size(max = 10) Map<@Size(max = 32) String, @Size(max = 128) String> meta) {
}
