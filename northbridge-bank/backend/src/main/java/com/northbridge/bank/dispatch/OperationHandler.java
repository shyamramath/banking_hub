package com.northbridge.bank.dispatch;

/**
 * One handler per operation. Handlers are thin: they delegate to services, which hold the
 * business logic and the object-level (ownership) checks.
 *
 * @param <I> typed input; the dispatcher binds and validates the JSON payload into it
 * @param <O> output, serialized into {@code ApiResponse.data}
 */
public interface OperationHandler<I, O> {

    Class<I> inputType();

    O handle(I input, OperationContext ctx);

    /**
     * What to write in the audit log for this input. Keep it free of secrets and full
     * account numbers - mask them. Defaults to nothing.
     */
    default String auditSummary(I input) {
        return "";
    }
}
