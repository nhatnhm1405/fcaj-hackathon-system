package com.fcaj.hackathon.security.oauth2;

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

        log.warn("oauth_login outcome=failed reason={}", reason);

        response.sendRedirect(frontendUrl + "/login?error="
                + URLEncoder.encode(reason, StandardCharsets.UTF_8));
    }

    private String resolveReason(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
            String errorCode = oauth2Exception.getError().getErrorCode();
            if (errorCode != null && java.util.Set.of("email_not_found","email_not_verified","provider_id_missing",
                    "account_changed_retry","account_link_required","account_provider_mismatch","authorization_request_not_found","invalid_state_parameter",
                    "access_denied","invalid_token_response","invalid_user_info_response","invalid_request").contains(errorCode)) {
                return errorCode.toUpperCase(java.util.Locale.ROOT);
            }
        }
        return "OAUTH2_FAILED";
    }
}
