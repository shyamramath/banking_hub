package com.northbridge.bank.dispatch;

import com.northbridge.bank.security.CurrentUser;
import java.util.Map;

/** Everything a handler may know about the call besides its input. */
public record OperationContext(String operation, CurrentUser user, String correlationId, Map<String, String> meta) {
}
