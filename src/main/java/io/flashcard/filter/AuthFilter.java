package io.flashcard.filter;

import io.flashcard.service.AuthService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

@Component
@Order(2)
public class AuthFilter implements Filter {

    /** Set when the request was authenticated with a session token rather than the learner id alone. */
    public static final String SESSION_ATTRIBUTE = "viaSession";

    private final AuthService auth;

    public AuthFilter(AuthService auth) {
        this.auth = auth;
    }

    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/api/levels", "/api/categories", "/api/idioms", "/api/schedules", "/api/users"
    );

    private static final Set<String> PUBLIC_PREFIXES = Set.of(
        "/api/idioms/", "/api/audio/", "/api/exam-media/", "/api/admin/"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String path = req.getRequestURI();

        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        if (isPublicPath(path, req.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // Signed-in apps send a session token; it decides who the request is for
        String authorization = req.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            Optional<Long> chatId = auth.resolveSession(authorization.substring(7).trim());
            if (chatId.isEmpty()) {
                // The code tells the app to drop its token; other 401s must not cost it the session
                res.setStatus(401);
                res.setContentType("application/json");
                res.getWriter().write("{\"error\":\"Session expired, sign in again\",\"code\":\"session_invalid\"}");
                return;
            }
            req.setAttribute("userId", chatId.get());
            req.setAttribute(SESSION_ATTRIBUTE, true);
            chain.doFilter(request, response);
            return;
        }

        // Apps that have not signed in identify with the learner id alone (the widget too)
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
        if (path.equals("/api/users") && "POST".equals(method)) return true;
        if (path.equals("/api/users/auto") && "POST".equals(method)) return true;
        if (path.equals("/api/auth/google") && "POST".equals(method)) return true;
        return false;
    }

    private void sendError(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.getWriter().write("{\"error\":\"" + message.replace("\"", "\\\"") + "\"}");
    }
}
