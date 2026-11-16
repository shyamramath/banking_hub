package com.example.ensenta.client;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.core.SoapActionCallback;

import com.example.ensenta.config.EnsentaProperties;
// Generated from the WSDLs by jaxb-maven-plugin. Class and getter names below follow the
// operation and field names in Jack Henry's guide; adjust them to what the WSDL actually generates.
import com.example.ensenta.gen.sso.*;
import com.example.ensenta.gen.deposit.*;

/**
 * Thin, typed wrapper over the Ensenta Mobile API (Multi-Check, SOAP/XML).
 * This is the ONLY class that touches generated JAXB types; everything above it
 * works with the gateway's own records.
 *
 * Workflow (Check Deposit Guide):
 *   StartSession -> CreateDepositBatch -> AddDepositItem (per check) -> GetDepositBatchSummary
 *   -> GetDepositItem (show risk factors) -> [UpdateDepositItems] -> PostDepositBatch -> EndSession
 */
@Component
public class EnsentaClient {

    private final WebServiceTemplate sso;
    private final WebServiceTemplate deposit;
    private final EnsentaProperties props;

    public EnsentaClient(WebServiceTemplate ssoTemplate, WebServiceTemplate depositTemplate, EnsentaProperties props) {
        this.sso = ssoTemplate;
        this.deposit = depositTemplate;
        this.props = props;
    }

    // ---------------------------------------------------------------- SSO service

    /** Our app has already authenticated and authorised the user; we pass identity + eligible accounts. */
    public SessionResult startSession(DepositorContext ctx, AccountHolder holder) {
        StartSessionRequest req = new StartSessionRequest();
        req.setPartnerId(props.partnerId());
        req.setLanguageId(props.languageId());
        req.setDepositorIpAddress(ctx.depositorIpAddress());
        req.setDeviceIdentifier(ctx.deviceIdentifier());
        req.setLocalDateTime(ctx.localDateTime());
        req.setTimeZone(ctx.timeZone());
        req.setAccountHolderNumber(holder.accountHolderNumber());
        req.setAccountHolderName(holder.name());
        req.setUsername(holder.username());
        req.setEmail(holder.email());

        ArrayOfAccount accounts = new ArrayOfAccount();
        for (AccountHolder.Account a : holder.accounts()) {
            Account acct = new Account();
            acct.setAccountNumber(a.number());
            acct.setDescription(a.description());
            acct.setAccountTypeCode(a.typeCode());      // values per Jack Henry docs/WSDL
            acct.setTag(0);
            accounts.getAccount().add(acct);
        }
        req.setAccounts(accounts);

        StartSession call = new StartSession();
        call.setRequest(req);
        StartSessionResponse resp = (StartSessionResponse) sso.marshalSendAndReceive(
                call, soapAction(props.soapActionBaseSso(), "StartSession"));

        var r = resp.getStartSessionResult();
        EnsentaException.check(r.getResponseCode(), r.getResponseMessage());
        return new SessionResult(
                r.getSessionToken(),
                r.getSessionStateId(),
                r.getAccountKeys().getAccountKey().stream()
                        .map(k -> new SessionResult.AccountKey(k.getAccountNumber(), k.getAccountNumberIndex()))
                        .toList());
    }

    public void endSession(DepositorContext ctx) {
        EndSessionRequest req = new EndSessionRequest();
        fillCommon(req, ctx);
        EndSession call = new EndSession();
        call.setRequest(req);
        sso.marshalSendAndReceive(call, soapAction(props.soapActionBaseSso(), "EndSession"));
    }

    // ---------------------------------------------------------------- Deposit service

    public long createDepositBatch(DepositorContext ctx, int accountNumberIndex, int itemCount, String description) {
        CreateDepositBatchRequest req = new CreateDepositBatchRequest();
        fillCommon(req, ctx);
        req.setDepositToAccountNumberIndex(accountNumberIndex);
        req.setItemCount(itemCount);
        req.setDescription(description);
        req.setAmount(BigDecimal.ZERO);           // docs: Amount is unused, send 0.00

        CreateDepositBatch call = new CreateDepositBatch();
        call.setRequest(req);
        var r = ((CreateDepositBatchResponse) deposit.marshalSendAndReceive(
                call, soapAction(props.soapActionBaseDeposit(), "CreateDepositBatch"))).getCreateDepositBatchResult();
        EnsentaException.check(r.getResponseCode(), r.getResponseMessage());
        return r.getDepositBatchId();
    }

    public void addDepositItem(DepositorContext ctx, long batchId, int itemNumber, BigDecimal amount,
                               String frontBase64, String backBase64) {
        AddDepositItemRequest req = new AddDepositItemRequest();
        fillCommon(req, ctx);
        req.setDepositBatchId(batchId);
        req.setItemNumber(itemNumber);
        req.setIsFirstItem(itemNumber == 1);
        req.setTenderType(1);                     // value used in Jack Henry's example
        req.setAmount(amount);                    // user-entered amount
        req.setCheckImageCategory(1);             // example value; confirm with EPS
        req.setImagePreprocessState(2);           // example value; depends on Mitek MiSnap processing
        req.setFrontBase64Image(frontBase64);
        req.setBackBase64Image(backBase64);

        AddDepositItem call = new AddDepositItem();
        call.setRequest(req);
        var r = ((AddDepositItemResponse) deposit.marshalSendAndReceive(
                call, soapAction(props.soapActionBaseDeposit(), "AddDepositItem"))).getAddDepositItemResult();
        EnsentaException.check(r.getResponseCode(), r.getResponseMessage());
    }

    public BatchSummary getDepositBatchSummary(DepositorContext ctx, long batchId) {
        GetDepositBatchSummaryRequest req = new GetDepositBatchSummaryRequest();
        fillCommon(req, ctx);
        req.setDepositBatchId(batchId);
        GetDepositBatchSummary call = new GetDepositBatchSummary();
        call.setRequest(req);
        var r = ((GetDepositBatchSummaryResponse) deposit.marshalSendAndReceive(
                call, soapAction(props.soapActionBaseDeposit(), "GetDepositBatchSummary"))).getGetDepositBatchSummaryResult();
        EnsentaException.check(r.getResponseCode(), r.getResponseMessage());
        return new BatchSummary(r.getItemCount(), r.getOkItemCount(), r.getNeedReviewItemCount(),
                r.getItemNumbers().getInt());
    }

    public DepositItem getDepositItem(DepositorContext ctx, long batchId, int itemNumber) {
        GetDepositItemRequest req = new GetDepositItemRequest();
        fillCommon(req, ctx);
        req.setDepositBatchId(batchId);
        req.setItemNumber(itemNumber);
        GetDepositItem call = new GetDepositItem();
        call.setRequest(req);
        var r = ((GetDepositItemResponse) deposit.marshalSendAndReceive(
                call, soapAction(props.soapActionBaseDeposit(), "GetDepositItem"))).getGetDepositItemResult();
        EnsentaException.check(r.getResponseCode(), r.getResponseMessage());
        var item = r.getDepositItem();
        return new DepositItem(
                itemNumber,
                item.getMicr(),
                item.getCheckNumber(),
                item.getOcrAmount(),
                item.getUserAmount(),
                item.getAmount(),
                item.getStatus(),
                item.getFundsAvailabilityText(),
                item.getRiskFactors().getRiskFactor().stream()
                        .map(f -> new DepositItem.RiskFactor(f.getRiskFactorId(), f.getDescription(),
                                f.getSeverity(), f.isIsFixable(), f.isIsConfirmable()))
                        .toList());
    }

    /** Delete an item or confirm/correct it (Amount, Micr, IsConfirmed, images). */
    public void updateDepositItem(DepositorContext ctx, long batchId, int itemNumber,
                                  Boolean delete, Boolean confirm, BigDecimal correctedAmount) {
        UpdateDepositItemsRequest req = new UpdateDepositItemsRequest();
        fillCommon(req, ctx);
        req.setDepositBatchId(batchId);
        DepositItemUpdate u = new DepositItemUpdate();
        u.setItemNumber(itemNumber);
        if (delete != null) u.setIsDeleted(delete);
        if (confirm != null) u.setIsConfirmed(confirm);
        if (correctedAmount != null) u.setAmount(correctedAmount);
        ArrayOfDepositItemUpdate list = new ArrayOfDepositItemUpdate();
        list.getDepositItemUpdate().add(u);
        req.setDepositItems(list);

        UpdateDepositItems call = new UpdateDepositItems();
        call.setRequest(req);
        var r = ((UpdateDepositItemsResponse) deposit.marshalSendAndReceive(
                call, soapAction(props.soapActionBaseDeposit(), "UpdateDepositItems"))).getUpdateDepositItemsResult();
        EnsentaException.check(r.getResponseCode(), r.getResponseMessage());
    }

    public PostResult postDepositBatch(DepositorContext ctx, long batchId, List<Integer> itemNumbers) {
        PostDepositBatchRequest req = new PostDepositBatchRequest();
        fillCommon(req, ctx);
        req.setDepositBatchId(batchId);
        ArrayOfInt items = new ArrayOfInt();
        items.getInt().addAll(itemNumbers);
        req.setItemNumbers(items);

        PostDepositBatch call = new PostDepositBatch();
        call.setRequest(req);
        var r = ((PostDepositBatchResponse) deposit.marshalSendAndReceive(
                call, soapAction(props.soapActionBaseDeposit(), "PostDepositBatch"))).getPostDepositBatchResult();
        EnsentaException.check(r.getResponseCode(), r.getResponseMessage());
        var s = r.getTransactionSummary();
        return new PostResult(s.getTransactionId(), s.getReceiptNumber(), s.getTotalAmount(),
                s.getNumberofChecks(), s.getStatus());
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Every deposit-service request carries partner, device and session fields.
     * If the WSDL's request types extend a common base type, JAXB generates it (named BaseRequest here).
     * If they don't, add a JAXB binding (xjc:superInterface) so they implement one interface,
     * or replace this with one small setter block per request type.
     */
    private void fillCommon(BaseRequest req, DepositorContext ctx) {
        req.setPartnerId(props.partnerId());
        req.setLanguageId(props.languageId());
        req.setDepositorIpAddress(ctx.depositorIpAddress());
        req.setDeviceIdentifier(ctx.deviceIdentifier());
        req.setLocalDateTime(ctx.localDateTime());
        req.setTimeZone(ctx.timeZone());
        req.setSessionToken(ctx.sessionToken());
        req.setSessionStateId(ctx.sessionStateId());
    }

    private static SoapActionCallback soapAction(String base, String operation) {
        return new SoapActionCallback(base + operation);
    }

    // ---------------------------------------------------------------- gateway-side records

    public record AccountHolder(String accountHolderNumber, String name, String username, String email,
                                List<Account> accounts) {
        public record Account(String number, String description, int typeCode) {}
    }

    public record SessionResult(String sessionToken, String sessionStateId, List<AccountKey> accountKeys) {
        public record AccountKey(String accountNumber, int accountNumberIndex) {}
    }

    public record BatchSummary(int itemCount, int okItemCount, int needReviewItemCount, List<Integer> itemNumbers) {}

    public record DepositItem(int itemNumber, String micr, String checkNumber, BigDecimal ocrAmount,
                              BigDecimal userAmount, BigDecimal amount, int status, String fundsAvailabilityText,
                              List<RiskFactor> riskFactors) {
        public record RiskFactor(int id, String description, String severity, boolean fixable, boolean confirmable) {}
    }

    public record PostResult(String transactionId, String receiptNumber, BigDecimal amount,
                             int numberOfChecks, String status) {}
}
