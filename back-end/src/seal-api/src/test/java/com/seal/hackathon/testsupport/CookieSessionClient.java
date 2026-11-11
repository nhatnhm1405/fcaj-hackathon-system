package com.seal.hackathon.testsupport;

import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Single HTTPS-origin MockMvc client. It deliberately does not add auth or CSRF headers. */
public final class CookieSessionClient {
    private final MockMvc mvc;
    private final Map<String, Cookie> cookies = new LinkedHashMap<>();
    private MockHttpSession session;

    public CookieSessionClient(MockMvc mvc) { this.mvc = mvc; }

    public Cookie cookie(String name) { return cookies.get(name); }
    public Set<String> cookieNames() { return Set.copyOf(cookies.keySet()); }
    public void putCookie(Cookie cookie) { cookies.put(cookie.getName(), cookie); }
    public void removeCookie(String name) { cookies.remove(name); }
    public MockHttpSession session() {
        if (session != null && session.isInvalid()) session = null;
        return session;
    }
    public void useSession(MockHttpSession session) { this.session = session; }

    public MvcResult perform(MockHttpServletRequestBuilder request) throws Exception {
        request.secure(true);
        if (session() != null) request.session(session);
        if (!cookies.isEmpty()) request.cookie(cookies.values().toArray(Cookie[]::new));
        MvcResult result = mvc.perform(request).andReturn();
        // Read the executed request without creating a session just to inspect it.
        session = (MockHttpSession) result.getRequest().getSession(false);
        if (session != null && session.isInvalid()) session = null;
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (cookie.getMaxAge() == 0) cookies.remove(cookie.getName());
            else cookies.put(cookie.getName(), cookie);
        }
        return result;
    }
}
