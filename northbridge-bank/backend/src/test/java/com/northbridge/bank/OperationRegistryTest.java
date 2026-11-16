package com.northbridge.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.northbridge.bank.dispatch.BankOperation;
import com.northbridge.bank.dispatch.NoInput;
import com.northbridge.bank.dispatch.OperationContext;
import com.northbridge.bank.dispatch.OperationHandler;
import com.northbridge.bank.dispatch.OperationRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The registry is the fail-closed guard: a handler without a policy must stop the app from starting. */
class OperationRegistryTest {

    static class NoPolicy implements OperationHandler<NoInput, String> {
        public Class<NoInput> inputType() { return NoInput.class; }
        public String handle(NoInput in, OperationContext ctx) { return "oops"; }
    }

    @BankOperation(name = "demo.ok", scopes = "accounts:read")
    static class Ok implements OperationHandler<NoInput, String> {
        public Class<NoInput> inputType() { return NoInput.class; }
        public String handle(NoInput in, OperationContext ctx) { return "ok"; }
    }

    @BankOperation(name = "demo.ok", scopes = "accounts:read")
    static class Duplicate extends Ok {
    }

    @BankOperation(name = "demo.noscope", scopes = {})
    static class NoScope extends Ok {
    }

    @Test
    void handlerWithoutPolicyRefusesToStart() {
        assertThatThrownBy(() -> new OperationRegistry(List.of(new Ok(), new NoPolicy())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no @BankOperation policy");
    }

    @Test
    void duplicateNamesRefuseToStart() {
        assertThatThrownBy(() -> new OperationRegistry(List.of(new Ok(), new Duplicate())))
                .hasMessageContaining("Duplicate operation");
    }

    @Test
    void operationsMustRequireAScope() {
        assertThatThrownBy(() -> new OperationRegistry(List.of(new NoScope())))
                .hasMessageContaining("at least one scope");
    }

    @Test
    void validHandlersAreIndexedByName() {
        OperationRegistry registry = new OperationRegistry(List.of(new Ok()));
        assertThat(registry.find("demo.ok")).isPresent();
        assertThat(registry.find("demo.missing")).isEmpty();
    }
}
