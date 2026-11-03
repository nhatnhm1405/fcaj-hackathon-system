package com.seal.hackathon.security;

import com.seal.hackathon.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InactiveParticipantWriteFilterTest {

    private final InactiveParticipantWriteFilter filter = new InactiveParticipantWriteFilter();

    @BeforeEach
    void authenticateInactiveStudent() {
        User user = User.builder()
                .userId(56)
                .email("inactive.student@fpt.edu.vn")
                .fullName("Inactive Student")
                .userType("FPT_STUDENT")
                .isApproved(true)
                .isActive(false)
                .build();
        UserPrincipal principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                )
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void login_shouldPassThrough_whenInactiveStudentHasExistingAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertSame(request, chain.getRequest());
    }

    @Test
    void businessWrite_shouldRemainBlocked_forInactiveStudent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/teams");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("Your account is inactive"));
    }
}
