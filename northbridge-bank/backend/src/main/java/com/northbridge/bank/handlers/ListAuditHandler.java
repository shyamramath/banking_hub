package com.northbridge.bank.handlers;

import com.northbridge.bank.audit.AuditService;
import com.northbridge.bank.dispatch.BankOperation;
import com.northbridge.bank.dispatch.OperationContext;
import com.northbridge.bank.dispatch.OperationHandler;
import com.northbridge.bank.handlers.dto.Dtos.LimitInput;
import java.util.List;
import org.springframework.stereotype.Component;

/** Staff-only: requires the audit:read scope AND the auditor role. Customers are denied. */
@Component
@BankOperation(name = "audit.list", scopes = "audit:read", anyRole = "auditor", audit = true)
public class ListAuditHandler implements OperationHandler<LimitInput, List<AuditService.AuditEntry>> {

    private final AuditService audit;

    public ListAuditHandler(AuditService audit) {
        this.audit = audit;
    }

    @Override
    public Class<LimitInput> inputType() {
        return LimitInput.class;
    }

    @Override
    public List<AuditService.AuditEntry> handle(LimitInput input, OperationContext ctx) {
        return audit.latest(input.limitOrDefault(50));
    }
}
