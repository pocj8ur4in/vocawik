package com.vocawik.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Adds a resolved client IP as {@code X-Client-IP}. */
@Component
public class ClientIpHeaderFilter extends OncePerRequestFilter {

    private static final String CLIENT_IP_HEADER = "X-Client-IP";

    private final ClientIpResolver clientIpResolver;

    /**
     * Creates a response header filter that resolves client IP using trusted proxy rules.
     *
     * @param clientIpResolver resolver for selecting the effective client IP
     */
    public ClientIpHeaderFilter(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader(CLIENT_IP_HEADER, clientIpResolver.resolve(request));
        filterChain.doFilter(request, response);
    }
}
