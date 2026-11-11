package com.seal.hackathon.testsupport;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class CookieSessionClientTest {
    @Test void retainsServerCreatedSessionAcrossRequests() throws Exception {
        var client = new CookieSessionClient(MockMvcBuilders.standaloneSetup(new SessionHarness()).build());
        var created = client.perform(get("/remember"));
        assertThat(created.getRequest().getSession(false)).isNotNull();
        assertThat(created.getResponse().getCookie("JSESSIONID")).isNull();
        // The old cookie-only helper returns 401 here even though the server created a session.
        var recalled = client.perform(get("/recall"));
        assertThat(recalled.getResponse().getStatus()).as("server-created session must reach next request").isEqualTo(200);
        assertThat(recalled.getRequest().getSession(false)).isSameAs(created.getRequest().getSession(false));
    }

    @Test void keepsClientsIndependentAndDropsInvalidatedSessions() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new SessionHarness()).build();
        var first = new CookieSessionClient(mvc);
        var second = new CookieSessionClient(mvc);
        first.perform(get("/remember"));
        var oldSession = first.session();
        assertThat(second.perform(get("/recall")).getResponse().getStatus()).isEqualTo(401);
        second.perform(get("/remember"));
        assertThat(second.session()).isNotSameAs(oldSession);
        first.perform(get("/invalidate"));
        assertThat(oldSession.isInvalid()).isTrue();
        assertThat(first.session()).isNull();
        assertThat(first.perform(get("/recall")).getResponse().getStatus()).isEqualTo(401);
        assertThat(second.perform(get("/recall")).getResponse().getStatus()).isEqualTo(200);
        first.perform(get("/remember"));
        assertThat(first.session()).isNotSameAs(oldSession).isNotSameAs(second.session());
    }

    @Test void keepsCookieAttributesAndHonorsDeletionWithoutDroppingSession() throws Exception {
        var client = new CookieSessionClient(MockMvcBuilders.standaloneSetup(new SessionHarness()).build());
        client.perform(get("/remember"));
        var session = client.session();
        client.perform(get("/cookie"));
        assertThat(client.cookie("synthetic").isHttpOnly()).isTrue();
        assertThat(client.cookie("synthetic").getSecure()).isTrue();
        assertThat(client.cookie("synthetic").getPath()).isEqualTo("/");
        client.perform(get("/delete-cookie"));
        assertThat(client.cookieNames()).doesNotContain("synthetic");
        assertThat(client.session()).isSameAs(session);
        assertThat(client.perform(get("/recall")).getResponse().getStatus()).isEqualTo(200);
    }

    @RestController
    static class SessionHarness {
        @GetMapping("/remember") String remember(HttpServletRequest request) {
            request.getSession(true).setAttribute("marker", "synthetic-marker");
            return "remembered";
        }
        @GetMapping("/recall") ResponseEntity<String> recall(HttpServletRequest request) {
            var session = request.getSession(false);
            return session != null && "synthetic-marker".equals(session.getAttribute("marker"))
                    ? ResponseEntity.ok("recalled") : ResponseEntity.status(401).body("missing");
        }
        @GetMapping("/invalidate") void invalidate(HttpServletRequest request) { request.getSession(false).invalidate(); }
        @GetMapping("/cookie") void cookie(HttpServletResponse response) {
            Cookie cookie = new Cookie("synthetic", "marker");
            cookie.setSecure(true); cookie.setHttpOnly(true); cookie.setPath("/"); response.addCookie(cookie);
        }
        @GetMapping("/delete-cookie") void deleteCookie(HttpServletResponse response) {
            Cookie cookie = new Cookie("synthetic", ""); cookie.setMaxAge(0); cookie.setPath("/"); response.addCookie(cookie);
        }
    }
}
