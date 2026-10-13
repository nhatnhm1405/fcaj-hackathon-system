package com.seal.hackathon.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {

    @Test
    void csrfCookieUsesConfiguredSharedDomainInProduction() {
        SecurityConfig config = newConfig(true, " sealhackathon.io.vn ");

        String setCookie = saveCsrfToken(config.csrfTokenRepository());

        assertTrue(setCookie.contains("Domain=sealhackathon.io.vn"));
        assertTrue(setCookie.contains("Secure"));
    }

    @Test
    void csrfCookieRemainsHostOnlyWhenDomainIsBlank() {
        SecurityConfig config = newConfig(false, " ");

        String setCookie = saveCsrfToken(config.csrfTokenRepository());

        assertFalse(setCookie.contains("Domain="));
        assertFalse(setCookie.contains("Secure"));
    }

    private SecurityConfig newConfig(boolean secure, String domain) {
        SecurityConfig config = new SecurityConfig(
                null, null, null, null, null, null, null, null);
        ReflectionTestUtils.setField(config, "cookieSecure", secure);
        ReflectionTestUtils.setField(config, "csrfCookieDomain", domain);
        return config;
    }

    private String saveCsrfToken(CsrfTokenRepository repository) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        repository.saveToken(
                new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "test-token"),
                request,
                response);
        String setCookie = response.getHeader("Set-Cookie");
        assertNotNull(setCookie);
        return setCookie;
    }
}
