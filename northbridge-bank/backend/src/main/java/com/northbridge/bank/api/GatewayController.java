package com.northbridge.bank.api;

import com.northbridge.bank.config.BankProperties;
import com.northbridge.bank.dispatch.OperationDispatcher;
import com.northbridge.bank.web.CorrelationIdFilter;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The ONLY business controller. Everything goes through the dispatcher. */
@RestController
@RequestMapping("/api")
public class GatewayController {

    private final OperationDispatcher dispatcher;
    private final ErrorMapper errors;
    private final int batchMaxSize;

    public GatewayController(OperationDispatcher dispatcher, ErrorMapper errors, BankProperties props) {
        this.dispatcher = dispatcher;
        this.errors = errors;
        this.batchMaxSize = props.limits().batchMaxSize();
    }

    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<Object>> execute(@Valid @RequestBody ApiRequest request) {
        String cid = MDC.get(CorrelationIdFilter.MDC_KEY);
        Object result = dispatcher.dispatch(request, cid);
        return ResponseEntity.ok(ApiResponse.ok(request.operation(), result, cid));
    }

    /**
     * Executes several operations for one screen. Each item is authorized, rate-limited and
     * validated independently; one failure does not fail the others.
     * Sequential on purpose: the SecurityContext is thread-bound. To parallelise, wrap the
     * executor in {@code DelegatingSecurityContextExecutorService}.
     */
    @PostMapping("/execute/batch")
    public ResponseEntity<List<ApiResponse<Object>>> batch(@Valid @RequestBody BatchRequest batch) {
        if (batch.requests().size() > batchMaxSize) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BATCH_TOO_LARGE",
                    "A batch may contain at most " + batchMaxSize + " operations");
        }
        String cid = MDC.get(CorrelationIdFilter.MDC_KEY);
        List<ApiResponse<Object>> results = new ArrayList<>(batch.requests().size());
        for (ApiRequest item : batch.requests()) {
            try {
                results.add(ApiResponse.ok(item.operation(), dispatcher.dispatch(item, cid), cid));
            } catch (RuntimeException ex) {
                results.add(ApiResponse.fail(item.operation(), errors.map(ex).error(), cid));
            }
        }
        return ResponseEntity.ok(results);
    }
}
