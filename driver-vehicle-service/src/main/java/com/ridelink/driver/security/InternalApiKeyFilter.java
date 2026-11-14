package com.ridelink.driver.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private static final String INTERNAL_PATH_PATTERN = "/internal/**";

    private final String expectedApiKey;
    private final SecurityErrorResponseWriter errorResponseWriter;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public InternalApiKeyFilter(
            @Value("${security.internal-api-key}") String expectedApiKey,
            SecurityErrorResponseWriter errorResponseWriter) {
        this.expectedApiKey = expectedApiKey;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();

        String path = requestUri.substring(contextPath.length());

        return !pathMatcher.match(INTERNAL_PATH_PATTERN, path);
    }
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String suppliedApiKey = request.getHeader(INTERNAL_API_KEY_HEADER);

        if (!apiKeysMatch(suppliedApiKey, expectedApiKey)) {
            errorResponseWriter.write(
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "Missing or invalid internal API key",
                    request.getRequestURI());
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        "internal-service",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_INTERNAL")));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private boolean apiKeysMatch(String suppliedApiKey, String expectedApiKey) {
        if (suppliedApiKey == null
                || suppliedApiKey.isBlank()
                || expectedApiKey == null
                || expectedApiKey.isBlank()) {
            return false;
        }

        return MessageDigest.isEqual(
                suppliedApiKey.getBytes(StandardCharsets.UTF_8),
                expectedApiKey.getBytes(StandardCharsets.UTF_8));
    }
}