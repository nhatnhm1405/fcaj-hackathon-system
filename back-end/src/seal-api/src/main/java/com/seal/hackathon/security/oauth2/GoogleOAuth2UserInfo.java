package com.seal.hackathon.security.oauth2;

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
        return (String) attributes.get("email");
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
