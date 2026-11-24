package ch.bzz.config;

import ch.bzz.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Checks the "Authorization: Bearer ..." header for the protected endpoints
 * (/accounts, /bookings) and stores the project name from the token as a
 * request attribute. Requests without a valid token get 401.
 */
@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String PROJECT_ATTRIBUTE = "projectName";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "no-store");

        if (!isProtected(request)) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        Optional<String> projectName = Optional.empty();
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            projectName = jwtUtil.validateToken(header.substring(BEARER_PREFIX.length()).trim());
        }

        if (projectName.isEmpty()) {
            log.info("Rejected {} {}: missing or invalid token", request.getMethod(), request.getRequestURI());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        request.setAttribute(PROJECT_ATTRIBUTE, projectName.get());
        chain.doFilter(request, response);
    }

    private boolean isProtected(HttpServletRequest request) {
        // CORS preflight requests carry no token and must pass through.
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/accounts") || path.startsWith("/bookings");
    }
}
