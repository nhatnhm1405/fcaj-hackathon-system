package com.seal.hackathon.security.oauth2;

import com.seal.hackathon.entity.User;
import com.seal.hackathon.repository.UserRepository;
import com.seal.hackathon.security.UserPrincipal;
import com.seal.hackathon.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Replaces Spring's default OAuth2 user loading.
 *
 * Flow:
 * 1. Spring fetches the user info from Google/GitHub.
 * 2. We extract email, name, providerId, avatarUrl.
 * 3. If the user exists in DB → update avatar/provider info.
 * 4. If the user does NOT exist → create a new User (is_approved = false).
 * 5. Return UserPrincipal so the rest of the filter chain has the user's roles.
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId(); // "google" or "github"
        OAuth2UserInfo userInfo = extractUserInfo(registrationId, oAuth2User.getAttributes());

        String email = userInfo.getEmail();
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_not_found"),
                    "Email not provided by " + registrationId + ". " +
                    "For GitHub: ensure your email is public or the user:email scope is granted."
            );
        }

        String providerUpper = registrationId.toUpperCase(); // GOOGLE or GITHUB

        var existing = userRepository.findByEmailWithRoles(email);
        boolean isNewAccount = existing.isEmpty();
        User user = existing
                .map(e -> updateExistingUser(e, userInfo, providerUpper))
                .orElseGet(() -> createNewUser(userInfo, providerUpper));

        userRepository.save(user);

        // First-time OAuth sign-up gets the same welcome other users receive — the
        // approval path notifies local registrants, but OAuth accounts are created
        // here, so greet them at account creation.
        if (isNewAccount) {
            String providerLabel = providerUpper.charAt(0) + providerUpper.substring(1).toLowerCase();
            notificationService.createNotification(
                    user.getUserId(),
                    "Welcome to SEAL Hackathon!",
                    "Your account was created with " + providerLabel + ". Complete your profile to get started.",
                    "ACCOUNT");
        }

        // Truyền attributes từ provider để UserPrincipal thoả mãn interface OAuth2User
        return new UserPrincipal(user, oAuth2User.getAttributes());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private OAuth2UserInfo extractUserInfo(String registrationId, Map<String, Object> attributes) {
        return switch (registrationId) {
            case "google" -> new GoogleOAuth2UserInfo(attributes);
            case "github" -> new GithubOAuth2UserInfo(attributes);
            default -> throw new OAuth2AuthenticationException(
                    new OAuth2Error("unsupported_provider"),
                    "OAuth2 provider not supported: " + registrationId
            );
        };
    }

    private User createNewUser(OAuth2UserInfo userInfo, String provider) {
        return User.builder()
                .email(userInfo.getEmail())
                .fullName(userInfo.getName() != null ? userInfo.getName() : userInfo.getEmail())
                // OAuth gives us only name/email — the user must pick FPT_STUDENT /
                // EXTERNAL_STUDENT / STAFF on the complete-profile screen first.
                .userType("PENDING_PROFILE")
                .provider(provider)
                .providerId(userInfo.getProviderId())
                .avatarUrl(userInfo.getAvatarUrl())
                .isApproved(false)         // Must be approved by coordinator before login succeeds
                .isActive(true)
                .build();
    }

    private User updateExistingUser(User user, OAuth2UserInfo userInfo, String provider) {
        // Update avatar if it changed
        if (userInfo.getAvatarUrl() != null) {
            user.setAvatarUrl(userInfo.getAvatarUrl());
        }
        // Record provider info if user previously registered locally and now uses OAuth2
        if (user.getProviderId() == null && userInfo.getProviderId() != null) {
            user.setProvider(provider);
            user.setProviderId(userInfo.getProviderId());
        }
        return user;
    }
}
