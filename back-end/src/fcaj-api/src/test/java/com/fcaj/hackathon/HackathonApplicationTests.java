package com.fcaj.hackathon;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class HackathonApplicationTests {

    @Autowired
    private WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void csrfBootstrapAllowsLogoutAndInvalidTokenIsRejected() throws Exception {
        mvc.perform(post("/api/auth/logout")).andExpect(status().isForbidden());

        MvcResult bootstrap = mvc.perform(get("/api/csrf"))
                .andExpect(status().isNoContent()).andReturn();
        Cookie tokenCookie = bootstrap.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(tokenCookie);
        mvc.perform(post("/api/auth/logout").cookie(tokenCookie)
                .header("X-XSRF-TOKEN", "invalid-token")).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").cookie(tokenCookie)
                .header("X-XSRF-TOKEN", tokenCookie.getValue())).andExpect(status().isOk());
    }

    @Test
    void anonymousProfileRequestIsUnauthorized() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void mockJobComponentsAreAbsentWithoutOptInProfile() {
        org.assertj.core.api.Assertions.assertThat(context.getBeansOfType(
                com.fcaj.hackathon.mockdemo.LocalMockJobService.class)).isEmpty();
        org.assertj.core.api.Assertions.assertThat(context.getBeansOfType(
                com.fcaj.hackathon.mockdemo.LocalMockJobController.class)).isEmpty();
        org.assertj.core.api.Assertions.assertThat(context.getBeansOfType(
                com.fcaj.hackathon.mockdemo.LocalMockJobStore.class)).isEmpty();
    }

	@Test
	void contextLoads() {
	}

}
