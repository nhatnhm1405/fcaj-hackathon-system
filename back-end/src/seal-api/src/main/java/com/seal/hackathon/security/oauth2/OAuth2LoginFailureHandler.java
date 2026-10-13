package com.seal.hackathon.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Handles OAuth2 failures before they fall through to Spring's default /login
 * redirect. The app is an SPA, so provider failures must return to the frontend
 * and backend logs should keep the detailed cause for debugging.
 */
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginFailureHandler.class);

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        String reason = resolveReason(exception);

        log.error("OAuth2 login failed on {} with reason {}: {}",
                request.getRequestURI(),
                reason,
                exception.getMessage(),
                exception);

        response.sendRedirect(frontendUrl + "/login?error="
                + URLEncoder.encode(reason, StandardCharsets.UTF_8));
    }

    private String resolveReason(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
            String errorCode = oauth2Exception.getError().getErrorCode();
            if (errorCode != null && !errorCode.isBlank()) {
                return errorCode.toUpperCase();
            }
        }
        return "OAUTH2_FAILED";
    }
}
