package com.seal.hackathon.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * Spring's documented pattern for pairing CookieCsrfTokenRepository (raw
 * token in the XSRF-TOKEN cookie, read back via the X-XSRF-TOKEN header by
 * the SPA) with BREACH-safe XOR masking for any traditional form submission
 * (hidden _csrf field). Header requests skip the XOR/unmask step since the
 * cookie and header are never rendered into HTML, so BREACH doesn't apply.
 */
@Component
public class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

    private final CsrfTokenRequestHandler delegate = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        this.delegate.handle(request, response, csrfToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String headerValue = request.getHeader(csrfToken.getHeaderName());
        return StringUtils.hasText(headerValue)
                ? super.resolveCsrfTokenValue(request, csrfToken)
                : this.delegate.resolveCsrfTokenValue(request, csrfToken);
    }
}
