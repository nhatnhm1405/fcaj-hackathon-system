package com.fcaj.hackathon.config;

import com.fcaj.hackathon.controller.OperationalController;
import com.fcaj.hackathon.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OperationalTest {
    @Test void readinessReflectsDatabaseWhileLivenessDoesNot() {
        var jdbc=mock(JdbcTemplate.class);var health=new OperationalController(jdbc,"a".repeat(40));
        when(jdbc.queryForObject("SELECT 1",Integer.class)).thenReturn(1);
        assertThat(health.ready().getStatusCode().value()).isEqualTo(200);
        when(jdbc.queryForObject("SELECT 1",Integer.class)).thenThrow(new IllegalStateException("jdbc:mysql://private/password"));
        assertThat(health.ready().getStatusCode().value()).isEqualTo(503);assertThat(health.ready().getBody().toString()).doesNotContain("private","password");
        assertThat(health.live()).containsEntry("status","UP");assertThat(health.version()).containsEntry("revision","a".repeat(40));
    }
    @Test void requestIdIsBoundedAndDoesNotLeakBetweenRequests() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new OperationalController(mock(JdbcTemplate.class),"unsafe value"))
                .addFilters(new RequestIdFilter()).build();
        var response=mvc.perform(get("/health/live").header("X-Request-ID","untrusted text")).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getHeader("X-Request-ID")).matches("[a-f0-9-]{36}");assertThat(org.slf4j.MDC.get("requestId")).isNull();
        String id=java.util.UUID.randomUUID().toString();
        mvc.perform(get("/health/version").header("X-Request-ID",id)).andExpect(header().string("X-Request-ID",id))
                .andExpect(jsonPath("$.revision").value("unknown"));
    }
    @Test void genericExceptionDoesNotReturnInternalDetails() {
        var response=new GlobalExceptionHandler().handleGenericException(new IllegalStateException("SELECT password FROM private_table"),new MockHttpServletRequest());
        assertThat(response.getBody().getMessage()).isEqualTo("INTERNAL_ERROR");
    }
}
