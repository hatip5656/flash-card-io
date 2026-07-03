package io.flashcard.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

@Component
@Order(1)
public class RateLimitFilter implements Filter {

    private final Cache<String, Bucket> globalBuckets = buildCache(10_000);
    private final Cache<String, Bucket> flashcardBuckets = buildCache(5_000);
    private final Cache<String, Bucket> quizBuckets = buildCache(5_000);
    private final Cache<String, Bucket> adminBuckets = buildCache(1_000);

    private static Cache<String, Bucket> buildCache(int maxSize) {
        return Caffeine.newBuilder().maximumSize(maxSize).expireAfterAccess(Duration.ofMinutes(5)).build();
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

        Bucket global = globalBuckets.get(ip, k ->
            Bucket.builder().addLimit(Bandwidth.simple(100, Duration.ofMinutes(1))).build());
        if (!global.tryConsume(1)) {
            sendTooMany(res, "Too many requests, please try again later");
            return;
        }

        if (path.equals("/api/flashcards/next")) {
            Bucket bucket = flashcardBuckets.get(userKey, k ->
                Bucket.builder().addLimit(Bandwidth.simple(10, Duration.ofMinutes(1))).build());
            if (!bucket.tryConsume(1)) {
                sendTooMany(res, "Too many flashcard requests, please wait");
                return;
            }
        }

        if (path.startsWith("/api/quiz/") || path.startsWith("/api/mobile/quiz/")) {
            Bucket bucket = quizBuckets.get(userKey, k ->
                Bucket.builder().addLimit(Bandwidth.simple(30, Duration.ofMinutes(1))).build());
            if (!bucket.tryConsume(1)) {
                sendTooMany(res, "Too many quiz requests, please wait");
                return;
            }
        }

        if (path.startsWith("/api/admin/")) {
            Bucket bucket = adminBuckets.get(ip, k ->
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
        res.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
