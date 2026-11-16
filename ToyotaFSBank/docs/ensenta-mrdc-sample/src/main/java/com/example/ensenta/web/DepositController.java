package com.example.ensenta.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.example.ensenta.client.DepositorContext;
import com.example.ensenta.client.EnsentaClient.PostResult;
import com.example.ensenta.client.EnsentaException;
import com.example.ensenta.service.MobileDepositService;
import com.example.ensenta.service.MobileDepositService.CheckImage;
import com.example.ensenta.service.MobileDepositService.ItemDecision;
import com.example.ensenta.service.MobileDepositService.ReviewResult;

/**
 * Simple JSON API for the mobile app. The app never sees Ensenta credentials,
 * the client certificate, or session tokens.
 *
 * Assumes Spring Security already authenticated the user (add spring-boot-starter-security
 * and your OAuth2 resource-server config).
 */
@RestController
@RequestMapping("/api/mobile-deposits")
public class DepositController {

    private final MobileDepositService deposits;

    public DepositController(MobileDepositService deposits) {
        this.deposits = deposits;
    }

    public record SubmitRequest(
            @NotBlank String toAccountNumber,
            String description,
            @NotBlank String deviceId,
            @NotBlank String timeZone,
            @NotEmpty @Size(max = 20) List<CheckImage> checks) {}   // 20 matches Banno's batch limit; confirm yours with EPS

    public record ConfirmRequest(List<ItemDecision> decisions, @NotEmpty List<Integer> itemNumbers) {}

    @PostMapping
    public ReviewResult submit(@AuthenticationPrincipal(expression = "subject") String userId,
                               @Valid @RequestBody SubmitRequest body,
                               HttpServletRequest http) {
        DepositorContext device = new DepositorContext(
                clientIp(http), body.deviceId(), body.timeZone(), null, null);
        return deposits.submit(userId, device, body.toAccountNumber(), body.description(), body.checks());
    }

    @PostMapping("/{batchId}/post")
    public PostResult post(@AuthenticationPrincipal(expression = "subject") String userId,
                           @PathVariable long batchId,
                           @Valid @RequestBody ConfirmRequest body) {
        PostResult result = deposits.confirm(userId, batchId,
                body.decisions() == null ? List.of() : body.decisions(), body.itemNumbers());
        deposits.finish(userId);
        return result;
    }

    /** Translate Ensenta codes into responses the app can act on. */
    @ExceptionHandler(EnsentaException.class)
    public ResponseEntity<ErrorBody> onEnsenta(EnsentaException e) {
        HttpStatus status = switch (e.code().action) {
            case FIX_REQUEST -> HttpStatus.BAD_REQUEST;
            case SHOW_REJECT, SHOW_LIMIT -> HttpStatus.UNPROCESSABLE_ENTITY;
            case BLOCK_USER -> HttpStatus.FORBIDDEN;
            case RETRY_LATER -> HttpStatus.ACCEPTED;
            case RESTART_SESSION, RESTART_DEPOSIT -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status)
                .body(new ErrorBody(e.code().name(), e.code().description, e.code().action.name()));
    }

    public record ErrorBody(String code, String message, String action) {}

    private static String clientIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");   // trust only if set by your own load balancer
        return fwd != null ? fwd.split(",")[0].trim() : req.getRemoteAddr();
    }
}
