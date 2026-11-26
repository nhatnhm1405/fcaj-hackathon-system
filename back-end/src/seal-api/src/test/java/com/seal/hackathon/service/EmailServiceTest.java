package com.seal.hackathon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

class EmailServiceTest {
    private MimeMessage send(String from) throws Exception {
        var sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        var service = new EmailService(sender);
        ReflectionTestUtils.setField(service, "fromAddress", from);
        ReflectionTestUtils.setField(service, "frontendUrl", "https://example.test");
        service.sendAccountApprovedEmail("user@example.test", "Test User");
        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        return captor.getValue();
    }

    @Test void usesTheConfiguredSenderAddress() throws Exception {
        var message = send("no-reply@example.test");
        assertThat(message.getFrom()).containsExactly(new InternetAddress("no-reply@example.test"));
    }

    @Test void doesNotMarkAutomatedMailAsUrgent() throws Exception {
        var message = send("no-reply@example.test");
        for (String header : new String[] {"X-Priority", "Priority", "Importance", "X-MSMail-Priority"}) {
            assertThat(message.getHeader(header)).as(header).isNull();
        }
    }
}
