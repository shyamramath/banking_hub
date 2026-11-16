package com.northbridge.bank.web;

import com.northbridge.bank.config.BankProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * One generic endpoint accepts arbitrary JSON, so cap the body size before Jackson parses it.
 * Requests without a Content-Length (chunked) are rejected for the same reason.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;

    public RequestSizeLimitFilter(BankProperties props) {
        this.maxBytes = props.limits().maxBodyBytes();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equals(request.getMethod())) {
            long length = request.getContentLengthLong();
            if (length < 0 || length > maxBytes) {
                response.setStatus(length < 0 ? 411 : 413);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                        "{\"success\":false,\"error\":{\"code\":\"REQUEST_TOO_LARGE\",\"message\":\"Request body missing length or too large\"}}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
