package com.seal.hackathon.security.oauth2;

import java.util.Map;

/**
 * Parses the attributes map returned by GitHub's user API.
 *
 * GitHub attribute keys:
 *   id         → numeric user ID (returned as Integer)
 *   email      → may be null if user has set email to private
 *   name       → display name (may be null; fallback to login)
 *   login      → GitHub username
 *   avatar_url → profile picture URL
 *
 * NOTE: If email is null, GitHub requires an extra API call to
 * /user/emails (scope: user:email). The scope is already configured in
 * application.properties (github.scope=user:email), so Spring OAuth2
 * client will include it in the token, but the primary email must be
 * verified and public for it to appear in the main attributes.
 */
public class GithubOAuth2UserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;

    public GithubOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public String getEmail() {
        return (String) attributes.get("email");
    }

    @Override
    public String getName() {
        String name = (String) attributes.get("name");
        // Fall back to GitHub username if display name is not set
        return name != null ? name : (String) attributes.get("login");
    }

    @Override
    public String getProviderId() {
        // GitHub returns id as Integer
        Object id = attributes.get("id");
        return id != null ? String.valueOf(id) : null;
    }

    @Override
    public String getAvatarUrl() {
        return (String) attributes.get("avatar_url");
    }
}
