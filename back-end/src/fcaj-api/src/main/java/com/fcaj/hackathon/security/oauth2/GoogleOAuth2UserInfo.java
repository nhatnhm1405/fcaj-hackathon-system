package com.fcaj.hackathon.security.oauth2;

import java.util.Map;

/**
 * Parses the attributes map returned by Google's UserInfo endpoint.
 *
 * Google attribute keys:
 *   sub      → unique Google user ID
 *   email    → user's email
 *   name     → display name
 *   picture  → profile picture URL
 */
public class GoogleOAuth2UserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;

    public GoogleOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public String getEmail() {
        if(!Boolean.TRUE.equals(attributes.get("email_verified"))) {
            throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException(
                    new org.springframework.security.oauth2.core.OAuth2Error("email_not_verified"),"Verified Google email is required.");
        }
        String email=(String)attributes.get("email");
        return email==null?null:email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public boolean isAuthoritativeEmail() {
        String email=getEmail();
        return email!=null && (email.endsWith("@gmail.com") || attributes.get("hd") instanceof String domain && !domain.isBlank());
    }

    @Override
    public String getName() {
        return (String) attributes.get("name");
    }

    @Override
    public String getProviderId() {
        return (String) attributes.get("sub");
    }

    @Override
    public String getAvatarUrl() {
        return (String) attributes.get("picture");
    }
}
