package com.northbridge.bank.dispatch;

import com.northbridge.bank.api.ApiException;
import com.northbridge.bank.api.ApiRequest;
import com.northbridge.bank.audit.AuditService;
import com.northbridge.bank.security.CurrentUser;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * The heart of the single-endpoint design. For every operation, in this order:
 * <ol>
 *   <li>resolve the handler - unknown names are denied exactly like forbidden ones</li>
 *   <li>function-level authz: required scopes + roles from {@link BankOperation}</li>
 *   <li>rate limit per (user, operation)</li>
 *   <li>strict payload binding + Bean Validation</li>
 *   <li>invoke; the service performs object-level (ownership) checks</li>
 *   <li>metrics tagged by operation, audit record for audited ops and every denial</li>
 * </ol>
 */
@Service
public class OperationDispatcher {

    private final OperationRegistry registry;
    private final PolicyEnforcer enforcer;
    private final OperationRateLimiter rateLimiter;
    private final PayloadBinder binder;
    private final AuditService audit;
    private final MeterRegistry meters;

    public OperationDispatcher(OperationRegistry registry, PolicyEnforcer enforcer, OperationRateLimiter rateLimiter,
                               PayloadBinder binder, AuditService audit, MeterRegistry meters) {
        this.registry = registry;
        this.enforcer = enforcer;
        this.rateLimiter = rateLimiter;
        this.binder = binder;
        this.audit = audit;
        this.meters = meters;
    }

    public Object dispatch(ApiRequest request, String correlationId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        CurrentUser user = CurrentUser.from(auth);
        MDC.put("user", user.username());
        MDC.put("operation", request.operation());

        OperationRegistry.RegisteredOperation op = registry.find(request.operation()).orElse(null);
        // Metric tag must be bounded: never tag with an attacker-supplied operation name.
        String metricName = op == null ? "unknown" : op.name();
        Timer.Sample sample = Timer.start(meters);
        String outcome = "success";
        Object input = null;

        try {
            if (op == null) {
                throw ApiException.notPermitted(request.operation(), "no such operation");
            }
            enforcer.check(op, user);
            rateLimiter.acquire(user.id(), op.name(), op.policy().ratePerMinute());
            input = binder.bind(request.payload(), op.handler().inputType());

            OperationContext ctx = new OperationContext(op.name(), user, correlationId,
                    request.meta() == null ? Map.of() : Map.copyOf(request.meta()));
            return invoke(op.handler(), input, ctx);

        } catch (ApiException e) {
            outcome = e.status().value() == 403 ? "denied" : "rejected";
            throw e;
        } catch (RuntimeException e) {
            outcome = "error";
            throw e;
        } finally {
            sample.stop(Timer.builder("bank.operation")
                    .tag("operation", metricName)
                    .tag("outcome", outcome)
                    .register(meters));

            boolean audited = op != null && op.policy().audit();
            if (audited || "denied".equals(outcome)) {
                audit.record(user, request.operation(), outcome, correlationId,
                        op != null && input != null ? summary(op.handler(), input) : "");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <I, O> O invoke(OperationHandler<I, O> handler, Object input, OperationContext ctx) {
        return handler.handle((I) input, ctx);
    }

    @SuppressWarnings("unchecked")
    private static <I> String summary(OperationHandler<I, ?> handler, Object input) {
        try {
            return handler.auditSummary((I) input);
        } catch (RuntimeException e) {
            return "";
        }
    }
}
