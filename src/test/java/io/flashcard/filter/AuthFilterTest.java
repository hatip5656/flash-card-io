package io.flashcard.filter;

import io.flashcard.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthFilterTest {

    private final AuthService auth = mock(AuthService.class);
    private final AuthFilter filter = new AuthFilter(auth);

    @Test
    void publicEndpointsPassWithoutAuth() throws Exception {
        String[] publicPaths = {"/api/levels", "/api/categories", "/api/idioms", "/api/idioms/random", "/api/schedules", "/api/audio/tere"};
        for (String path : publicPaths) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(req, res, chain);

            assertEquals(200, res.getStatus(), "Should pass for " + path);
            assertNotNull(chain.getRequest(), "Chain should continue for " + path);
        }
    }

    @Test
    void postUsersIsPublic() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/users");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals(200, res.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void postUsersAutoIsPublic() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/users/auto");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals(200, res.getStatus());
    }

    @Test
    void rejectsAuthenticatedEndpointWithoutHeader() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users/me");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals(401, res.getStatus());
        assertTrue(res.getContentAsString().contains("Missing or invalid"));
    }

    @Test
    void rejectsNonNumericUserId() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users/me");
        req.addHeader("X-User-Id", "abc");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals(401, res.getStatus());
    }

    @Test
    void acceptsValidUserId() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users/me");
        req.addHeader("X-User-Id", "12345");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals(200, res.getStatus());
        assertEquals(12345L, req.getAttribute("userId"));
    }

    @Test
    void nonApiPathsPassThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals(200, res.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void acceptsUserIdFromQueryParam() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users/me");
        req.setParameter("userId", "67890");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals(200, res.getStatus());
        assertEquals(67890L, req.getAttribute("userId"));
    }

    @Test
    void sessionTokenDecidesTheUser() throws Exception {
        when(auth.resolveSession("good-token")).thenReturn(Optional.of(2000000042L));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users/me");
        req.addHeader("Authorization", "Bearer good-token");
        // A stale learner id next to the token must not win
        req.addHeader("X-User-Id", "123");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals(200, res.getStatus());
        assertEquals(2000000042L, req.getAttribute("userId"));
    }

    @Test
    void unknownSessionTokenIsRejected() throws Exception {
        when(auth.resolveSession("revoked")).thenReturn(Optional.empty());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users/me");
        req.addHeader("Authorization", "Bearer revoked");
        req.addHeader("X-User-Id", "123");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals(401, res.getStatus());
        assertTrue(res.getContentAsString().contains("\"code\":\"session_invalid\""));
        assertNull(chain.getRequest());
    }

    @Test
    void googleSignInIsPublic() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/google");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertNotNull(chain.getRequest());
        verifyNoInteractions(auth);
    }
}
