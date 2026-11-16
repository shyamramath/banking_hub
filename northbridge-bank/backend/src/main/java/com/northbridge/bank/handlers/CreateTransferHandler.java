package com.northbridge.bank.handlers;

import com.northbridge.bank.dispatch.BankOperation;
import com.northbridge.bank.dispatch.OperationContext;
import com.northbridge.bank.dispatch.OperationHandler;
import com.northbridge.bank.handlers.dto.TransferReceipt;
import com.northbridge.bank.handlers.dto.TransferRequest;
import com.northbridge.bank.service.TransferService;
import org.springframework.stereotype.Component;

/** Money movement: needs a write scope, the customer role, is audited and tightly rate-limited. */
@Component
@BankOperation(name = "transfer.create", scopes = "transfers:write", anyRole = "customer",
        audit = true, ratePerMinute = 5)
public class CreateTransferHandler implements OperationHandler<TransferRequest, TransferReceipt> {

    private final TransferService transfers;

    public CreateTransferHandler(TransferService transfers) {
        this.transfers = transfers;
    }

    @Override
    public Class<TransferRequest> inputType() {
        return TransferRequest.class;
    }

    @Override
    public TransferReceipt handle(TransferRequest input, OperationContext ctx) {
        return transfers.transfer(ctx.user(), input);
    }

    @Override
    public String auditSummary(TransferRequest in) {
        String to = in.toAccountId() != null ? "own:" + in.toAccountId() : "payee:" + in.maskedPayee();
        return "from=" + in.fromAccountId() + " to=" + to + " amount=" + in.amount().toPlainString()
                + " idem=" + in.idempotencyKey();
    }
}
