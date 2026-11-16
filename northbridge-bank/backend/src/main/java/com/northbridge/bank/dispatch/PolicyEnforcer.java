package com.northbridge.bank.dispatch;

import com.northbridge.bank.api.ApiException;
import com.northbridge.bank.security.CurrentUser;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Function-level authorization: does this token + user may call this operation at all?
 * Object-level authorization (may they touch THIS account?) lives in the services.
 */
@Component
public class PolicyEnforcer {

    public void check(OperationRegistry.RegisteredOperation op, CurrentUser user) {
        BankOperation policy = op.policy();

        List<String> missingScopes = Arrays.stream(policy.scopes())
                .filter(scope -> !user.hasAuthority("SCOPE_" + scope))
                .toList();
        if (!missingScopes.isEmpty()) {
            throw ApiException.notPermitted(op.name(), "token lacks scopes " + missingScopes);
        }

        String[] roles = policy.anyRole();
        boolean roleOk = roles.length == 0 || Arrays.stream(roles).anyMatch(r -> user.hasAuthority("ROLE_" + r));
        if (!roleOk) {
            throw ApiException.notPermitted(op.name(), "user has none of roles " + Arrays.toString(roles));
        }
    }
}
