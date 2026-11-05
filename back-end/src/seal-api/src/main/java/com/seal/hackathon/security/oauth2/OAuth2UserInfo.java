package com.seal.hackathon.security.oauth2;

/**
 * Abstracts over the different attribute structures returned by
 * Google (uses "sub" for ID, "picture" for avatar) and
 * GitHub (uses "id" for ID, "avatar_url" for avatar).
 */
public interface OAuth2UserInfo {

    String getEmail();

    String getName();

    // The unique user ID within the provider's system
    String getProviderId();

    // Profile picture URL — may be null
    String getAvatarUrl();
}
