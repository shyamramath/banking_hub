package com.northbridge.bank.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Propagates the BFF's correlation id (or creates one) into MDC and the response, so a single
 * user action can be traced UI -> BFF -> backend even though every call hits the same URL.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        // Never log attacker-controlled garbage: accept only a safe format.
        String cid = incoming != null && SAFE.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, cid);
        response.setHeader(HEADER, cid);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
