package io.flashcard.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(1)
public class RateLimitFilter implements Filter {

    private final ConcurrentHashMap<String, Bucket> globalBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> flashcardBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> quizBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> adminBuckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public RateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

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

        String ip = req.getRemoteAddr();
        String userId = req.getHeader("X-User-Id");
        String userKey = userId != null ? userId : "anonymous";

        // Global: 100/min per IP
        Bucket global = globalBuckets.computeIfAbsent(ip, k ->
            Bucket.builder().addLimit(Bandwidth.simple(100, Duration.ofMinutes(1))).build());
        if (!global.tryConsume(1)) {
            sendTooMany(res, "Too many requests, please try again later");
            return;
        }

        // Flashcard: 10/min per user
        if (path.equals("/api/flashcards/next")) {
            Bucket bucket = flashcardBuckets.computeIfAbsent(userKey, k ->
                Bucket.builder().addLimit(Bandwidth.simple(10, Duration.ofMinutes(1))).build());
            if (!bucket.tryConsume(1)) {
                sendTooMany(res, "Too many flashcard requests, please wait");
                return;
            }
        }

        // Quiz: 30/min per user
        if (path.startsWith("/api/quiz/") || path.startsWith("/api/mobile/quiz/")) {
            Bucket bucket = quizBuckets.computeIfAbsent(userKey, k ->
                Bucket.builder().addLimit(Bandwidth.simple(30, Duration.ofMinutes(1))).build());
            if (!bucket.tryConsume(1)) {
                sendTooMany(res, "Too many quiz requests, please wait");
                return;
            }
        }

        // Admin: 20/min per IP
        if (path.startsWith("/api/admin/")) {
            Bucket bucket = adminBuckets.computeIfAbsent(ip, k ->
                Bucket.builder().addLimit(Bandwidth.simple(20, Duration.ofMinutes(1))).build());
            if (!bucket.tryConsume(1)) {
                sendTooMany(res, "Too many admin requests, please wait");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private void sendTooMany(HttpServletResponse res, String message) throws IOException {
        res.setStatus(429);
        res.setContentType("application/json");
        objectMapper.writeValue(res.getOutputStream(), Map.of("error", message));
    }
}
