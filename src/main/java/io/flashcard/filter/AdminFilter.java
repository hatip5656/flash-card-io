package io.flashcard.filter;

import io.flashcard.service.JwtService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
public class AdminFilter implements Filter {

    private final JwtService jwtService;

    public AdminFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String path = req.getRequestURI();

        if (!path.startsWith("/api/admin/")) {
            chain.doFilter(request, response);
            return;
        }

        // Login endpoint is public
        if (path.equals("/api/admin/login") && "POST".equals(req.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // OPTIONS (CORS preflight) should pass through
        if ("OPTIONS".equals(req.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendError(res, 401, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7);
        String subject = jwtService.validateAndGetSubject(token);
        if (subject == null) {
            sendError(res, 401, "Invalid or expired token");
            return;
        }

        req.setAttribute("adminUser", subject);
        chain.doFilter(request, response);
    }

    private void sendError(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.getWriter().write("{\"error\":\"" + message.replace("\"", "\\\"") + "\"}");
    }
}
