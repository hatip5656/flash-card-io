package io.flashcard.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Component
@Order(2)
public class AuthFilter implements Filter {

    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/api/levels", "/api/categories", "/api/idioms", "/api/schedules", "/api/users"
    );

    private static final Set<String> PUBLIC_PREFIXES = Set.of(
        "/api/idioms/", "/api/audio/"
    );

    private final ObjectMapper objectMapper;

    public AuthFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String path = req.getRequestURI();

        // Skip non-API paths
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        // Public routes
        if (isPublicPath(path, req.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // Extract userId from header or query param
        String userIdStr = req.getHeader("X-User-Id");
        if (userIdStr == null) {
            userIdStr = req.getParameter("userId");
        }

        if (userIdStr == null || userIdStr.isBlank()) {
            sendError(res, 401, "Missing or invalid X-User-Id header");
            return;
        }

        try {
            long userId = Long.parseLong(userIdStr);
            req.setAttribute("userId", userId);
        } catch (NumberFormatException e) {
            sendError(res, 401, "Missing or invalid X-User-Id header");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isPublicPath(String path, String method) {
        if (PUBLIC_PATHS.contains(path)) return true;
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        // POST /api/users and POST /api/users/auto are public
        if (path.equals("/api/users") && "POST".equals(method)) return true;
        if (path.equals("/api/users/auto") && "POST".equals(method)) return true;
        return false;
    }

    private void sendError(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        objectMapper.writeValue(res.getOutputStream(), Map.of("error", message));
    }
}
