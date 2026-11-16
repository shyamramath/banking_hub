package com.northbridge.bank.dispatch;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

/**
 * Discovers every {@link OperationHandler} bean at startup and indexes it by operation name.
 * Startup FAILS if a handler has no {@link BankOperation} policy, declares no scopes, has an
 * invalid name, or duplicates another handler's name.
 */
@Component
public class OperationRegistry {

    private static final Logger log = LoggerFactory.getLogger(OperationRegistry.class);
    private static final Pattern NAME = Pattern.compile("[a-z]+(\\.[a-z]+)+");

    public record RegisteredOperation(String name, OperationHandler<?, ?> handler, BankOperation policy) {
    }

    private final Map<String, RegisteredOperation> operations;

    public OperationRegistry(List<OperationHandler<?, ?>> handlers) {
        Map<String, RegisteredOperation> found = new HashMap<>();
        for (OperationHandler<?, ?> handler : handlers) {
            Class<?> type = AopUtils.getTargetClass(handler);
            BankOperation policy = AnnotatedElementUtils.findMergedAnnotation(type, BankOperation.class);

            if (policy == null) {
                throw new IllegalStateException("Handler " + type.getName()
                        + " has no @BankOperation policy - refusing to start (fail closed)");
            }
            if (!NAME.matcher(policy.name()).matches()) {
                throw new IllegalStateException("Invalid operation name '" + policy.name() + "' on " + type.getName());
            }
            if (policy.scopes().length == 0) {
                throw new IllegalStateException("Operation " + policy.name() + " must require at least one scope");
            }
            if (policy.ratePerMinute() <= 0) {
                throw new IllegalStateException("Operation " + policy.name() + " needs a positive ratePerMinute");
            }
            RegisteredOperation previous = found.putIfAbsent(policy.name(), new RegisteredOperation(policy.name(), handler, policy));
            if (previous != null) {
                throw new IllegalStateException("Duplicate operation '" + policy.name() + "': "
                        + AopUtils.getTargetClass(previous.handler()).getName() + " and " + type.getName());
            }
        }
        this.operations = Map.copyOf(found);
        log.info("Registered {} operations: {}", operations.size(), operations.keySet().stream().sorted().toList());
    }

    public Optional<RegisteredOperation> find(String name) {
        return Optional.ofNullable(operations.get(name));
    }

    public Collection<RegisteredOperation> all() {
        return operations.values();
    }
}
