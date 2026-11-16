package com.northbridge.bank.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Several operations in one round trip (e.g. a dashboard). Each item is authorized on its own. */
public record BatchRequest(@NotEmpty List<@Valid ApiRequest> requests) {
}
