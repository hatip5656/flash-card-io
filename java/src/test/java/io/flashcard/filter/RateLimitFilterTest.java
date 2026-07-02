package io.flashcard.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter(new ObjectMapper());

    @Test
    void allowsNormalRequests() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/levels");
        req.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals(200, res.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void nonApiPathsSkipRateLimiting() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals(200, res.getStatus());
    }

    @Test
    void globalLimitEnforcedPerIp() throws Exception {
        String ip = "10.0.0.99";
        // Send 100 requests — all should pass
        for (int i = 0; i < 100; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/levels");
            req.setRemoteAddr(ip);
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
            assertEquals(200, res.getStatus(), "Request " + (i + 1) + " should pass");
        }

        // 101st should be rate limited
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/levels");
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        assertEquals(429, res.getStatus());
    }

    @Test
    void flashcardLimitEnforcedPerUser() throws Exception {
        String userId = "flashcard-test-user";
        // Send 10 flashcard requests — all should pass
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/flashcards/next");
            req.setRemoteAddr("192.168.1." + i); // different IPs to avoid global limit
            req.addHeader("X-User-Id", userId);
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
            assertEquals(200, res.getStatus(), "Flashcard request " + (i + 1) + " should pass");
        }

        // 11th should be rate limited
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/flashcards/next");
        req.setRemoteAddr("192.168.2.1");
        req.addHeader("X-User-Id", userId);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        assertEquals(429, res.getStatus());
    }
}
