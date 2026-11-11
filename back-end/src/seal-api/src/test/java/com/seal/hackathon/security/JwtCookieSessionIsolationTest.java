package com.seal.hackathon.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.hackathon.testsupport.CookieSessionClient;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Real login, CSRF, controllers and security; synthetic H2 data, no OAuth provider/browser/AWS. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:jwt_session_isolation;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=YEAR,VALUE",
        "app.bootstrap.admin.enabled=true", "app.bootstrap.admin.email=session-admin@example.test",
        "app.bootstrap.admin.password=Synthetic-Session-482!", "app.jwt.cookie.secure=true",
        "app.seed.scenario=NONE", "app.ai.jobs.enabled=false"})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JwtCookieSessionIsolationTest {
    private static final String EMAIL = "session-admin@example.test";
    private static final String CONTEXT = HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
    private static final ObjectMapper JSON = new ObjectMapper();
    @Autowired WebApplicationContext context;
    @Value("${app.jwt.secret}") String testKey;
    private MockMvc mvc;
    private final List<Map<String, Object>> trace = new ArrayList<>();

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void validJwtKeepsTransientSessionWithoutPersistingAuthentication() throws Exception {
        var client = clientWithTransientSession();
        login(client);
        var session = client.session();
        String xsrf = client.cookie("XSRF-TOKEN").getValue();
        for (int i = 0; i < 3; i++) {
            assertSessionCarried(call(client, get("/api/auth/me"), 200), session);
            assertTransientSession(client, session);
            assertThat(client.cookie("XSRF-TOKEN").getValue().equals(xsrf)).as("CSRF survives authenticated GET").isTrue();
        }
    }

    @Test void removingOnlyJwtRejectsSameStillValidSession() throws Exception {
        var client = clientWithTransientSession(); login(client);
        var session = client.session();
        var xsrf = client.cookie("XSRF-TOKEN");
        client.removeCookie(JwtCookieFactory.COOKIE_NAME);
        assertSessionCarried(call(client, get("/api/auth/me"), 401), session);
        assertThat(client.cookie("XSRF-TOKEN")).isSameAs(xsrf);
        assertTransientSession(client, session);
    }

    @Test void malformedJwtCannotFallBackToSameSession() throws Exception {
        var client = clientWithTransientSession(); login(client);
        var session = client.session();
        client.putCookie(new Cookie(JwtCookieFactory.COOKIE_NAME, "malformed-test-token"));
        assertSessionCarried(call(client, get("/api/auth/me"), 401), session);
        assertTransientSession(client, session);
    }

    @Test void correctlySignedButExpiredJwtCannotFallBackToSameSession() throws Exception {
        var client = clientWithTransientSession(); login(client);
        var session = client.session();
        var key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(testKey));
        Instant now = Instant.now();
        String expired = Jwts.builder().subject(EMAIL).issuedAt(Date.from(now.minusSeconds(120)))
                .expiration(Date.from(now.minusSeconds(60))).signWith(key).compact();
        // Distinguish expiration from signature failure before exercising the real filter.
        var exception = assertThrows(ExpiredJwtException.class,
                () -> Jwts.parser().verifyWith(key).build().parseSignedClaims(expired));
        assertThat(exception.getClaims().getSubject()).isEqualTo(EMAIL);
        assertThat(exception.getClaims().getExpiration().before(new Date())).isTrue();
        client.putCookie(new Cookie(JwtCookieFactory.COOKIE_NAME, expired));
        assertSessionCarried(call(client, get("/api/auth/me"), 401), session);
        assertTransientSession(client, session);
    }

    @Test void logoutDeletesCookiesAndOldSessionDoesNotAuthenticate() throws Exception {
        var client = clientWithTransientSession(); login(client);
        var session = client.session();
        var logout = call(client, csrf(client, post("/api/auth/logout")), 200);
        assertThat(logout.getResponse().getCookie(JwtCookieFactory.COOKIE_NAME).getMaxAge()).isZero();
        assertThat(logout.getResponse().getCookie("XSRF-TOKEN").getMaxAge()).isZero();
        assertThat(client.cookieNames()).doesNotContain(JwtCookieFactory.COOKIE_NAME, "XSRF-TOKEN");
        var unauthenticated = call(client, get("/api/auth/me"), 401);
        if (!session.isInvalid()) {
            assertSessionCarried(unauthenticated, session);
            assertTransientSession(client, session);
        } else {
            assertThat(client.session()).isNotSameAs(session);
        }
    }

    @Test void authenticatedContextAlreadyInSessionCannotReplaceJwt() throws Exception {
        var client = clientWithTransientSession();
        var session = client.session();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                org.springframework.security.core.userdetails.User.withUsername(EMAIL).password("unused")
                        .authorities("ROLE_SYSTEM_ADMIN").build(), null,
                List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN")));
        SecurityContext stale = new SecurityContextImpl(authentication);
        session.setAttribute(CONTEXT, stale);
        assertSessionCarried(call(client, get("/api/auth/me"), 401), session);
        // Do not erase the poisoned session in the harness to manufacture a 401.
        assertThat(session.getAttribute(CONTEXT)).isSameAs(stale);
    }

    @Test void csrfRemainsRequiredAndReloginWorksWithSameTransientSession() throws Exception {
        var client = clientWithTransientSession(); login(client);
        var session = client.session();
        String before = client.cookie("XSRF-TOKEN").getValue();
        assertSessionCarried(call(client, profileUpdate(), 403), session);
        call(client, csrf(client, profileUpdate()), 200);
        call(client, csrf(client, post("/api/auth/logout")), 200);
        call(client, get("/api/csrf"), 204);
        assertThat(client.cookie("XSRF-TOKEN").getValue().equals(before)).as("CSRF reissued after logout").isFalse();
        login(client);
        var currentSession = session.isInvalid() ? client.session() : session;
        assertSessionCarried(call(client, get("/api/auth/me"), 200), currentSession);
        assertSessionCarried(call(client, csrf(client, profileUpdate()), 200), currentSession);
        assertTransientSession(client, currentSession);
    }

    private CookieSessionClient clientWithTransientSession() {
        var client = new CookieSessionClient(mvc);
        var session = new MockHttpSession(context.getServletContext());
        session.setAttribute("transient-marker", "synthetic-oauth-state");
        client.useSession(session);
        return client;
    }

    private void login(CookieSessionClient client) throws Exception {
        call(client, get("/api/csrf"), 204);
        var result = call(client, csrf(client, post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"Synthetic-Session-482!\"}")), 200);
        var data = JSON.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.hasNonNull("token")).isFalse();
        assertThat(data.hasNonNull("tokenType")).isFalse();
        assertThat(client.cookie(JwtCookieFactory.COOKIE_NAME).isHttpOnly()).isTrue();
        assertThat(client.cookie(JwtCookieFactory.COOKIE_NAME).getSecure()).isTrue();
        call(client, get("/api/csrf"), 204);
        call(client, get("/api/auth/me"), 200);
        assertTransientSession(client, client.session());
    }

    private MockHttpServletRequestBuilder csrf(CookieSessionClient client, MockHttpServletRequestBuilder request) {
        return request.header("X-XSRF-TOKEN", client.cookie("XSRF-TOKEN").getValue());
    }
    private MockHttpServletRequestBuilder profileUpdate() {
        return put("/api/auth/me").contentType("application/json").content("{\"fullName\":\"Synthetic Session Admin\"}");
    }
    private MvcResult call(CookieSessionClient client, MockHttpServletRequestBuilder request, int expected) throws Exception {
        var result = client.perform(request);
        var executed = result.getRequest();
        trace.add(Map.of("method", executed.getMethod(), "path", executed.getRequestURI(),
                "http", result.getResponse().getStatus(), "sessionPresent", executed.getSession(false) != null,
                "authorizationHeaderPresent", executed.getHeader("Authorization") != null));
        assertThat(executed.getHeader("Authorization")).isNull();
        assertThat(result.getResponse().getStatus()).as(executed.getMethod() + " " + executed.getRequestURI()).isEqualTo(expected);
        return result;
    }
    private void assertSessionCarried(MvcResult result, MockHttpSession session) {
        assertThat(session).isNotNull();
        assertThat(result.getRequest().getSession(false)).isSameAs(session);
        assertThat(session.getAttribute("transient-marker")).isEqualTo("synthetic-oauth-state");
    }
    private void assertTransientSession(CookieSessionClient client, MockHttpSession session) {
        assertThat(client.session()).isSameAs(session).isNotNull();
        assertThat(session.getAttribute("transient-marker")).isEqualTo("synthetic-oauth-state");
        assertThat(session.getAttribute(CONTEXT)).as("JWT must not persist authentication in HttpSession").isNull();
    }

    @AfterEach void writeFilteredTrace(TestInfo info) throws Exception {
        Path output = Path.of("target/jwt-session-fix-20261103/auth-traces", info.getTestMethod().orElseThrow().getName() + ".json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                "executedAt", Instant.now().toString(), "scope", "MockMvc/H2; real security; no browser/provider/AWS", "steps", trace)) + "\n");
    }
}
