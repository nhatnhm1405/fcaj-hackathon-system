package com.fcaj.hackathon.security.oauth2;

import com.fcaj.hackathon.entity.User;
import com.fcaj.hackathon.repository.UserRepository;
import com.fcaj.hackathon.service.NotificationService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleOAuthIdentityTest {
    @Test void concurrentAccountChangeProducesSafeOAuthFailureBeforePrincipal() {
        var users=mock(UserRepository.class);var notifications=mock(NotificationService.class);
        var service=new CustomOAuth2UserService(users,notifications);
        when(users.findByEmailWithRoles("person@gmail.com")).thenReturn(Optional.of(User.builder().email("person@gmail.com").userType("STAFF").provider("GOOGLE").providerId("subject-1").build()));
        when(users.saveAndFlush(any())).thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(User.class,1));
        assertThatThrownBy(()->service.authenticate("google",provider(attributes("person@gmail.com",true,"subject-1"))))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,e->assertThat(e.getError().getErrorCode()).isEqualTo("account_changed_retry"));
        verifyNoInteractions(notifications);
    }
    private Map<String,Object> attributes(String email,boolean verified,String subject) {
        return new HashMap<>(Map.of("sub",subject,"email",email,"email_verified",verified,"name","Test user","picture","https://provider.example.test/avatar"));
    }
    private DefaultOAuth2User provider(Map<String,Object> attributes) {
        return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")),attributes,"sub");
    }
    @Test void unverifiedEmailIsRejectedBeforeDatabaseLookup() {
        var users=mock(UserRepository.class);var service=new CustomOAuth2UserService(users,mock(NotificationService.class));
        assertThatThrownBy(() -> service.authenticate("google",provider(attributes("person@gmail.com",false,"subject-1"))))
                .isInstanceOf(OAuth2AuthenticationException.class);verifyNoInteractions(users);
    }
    @Test void verifiedGoogleSignupNormalizesEmailAndRemainsUnapproved() {
        var users=mock(UserRepository.class);var notifications=mock(NotificationService.class);var service=new CustomOAuth2UserService(users,notifications);
        when(users.findByEmailWithRoles("person@gmail.com")).thenReturn(Optional.empty());
        service.authenticate("google",provider(attributes(" Person@Gmail.com ",true,"subject-1")));
        var saved=org.mockito.ArgumentCaptor.forClass(User.class);verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("person@gmail.com");assertThat(saved.getValue().getProviderId()).isEqualTo("subject-1");
        assertThat(saved.getValue().getIsApproved()).isFalse();assertThat(saved.getValue().getUserType()).isEqualTo("PENDING_PROFILE");
    }
    @Test void knownSubjectCanLoginWithoutReplacingUploadedAvatar() {
        var users=mock(UserRepository.class);var service=new CustomOAuth2UserService(users,mock(NotificationService.class));
        var user=User.builder().email("person@gmail.com").userType("STAFF").provider("GOOGLE").providerId("subject-1").avatarUrl("/uploads/avatars/custom.png").build();
        when(users.findByEmailWithRoles("person@gmail.com")).thenReturn(Optional.of(user));
        service.authenticate("google",provider(attributes("person@gmail.com",true,"subject-1")));
        assertThat(user.getAvatarUrl()).isEqualTo("/uploads/avatars/custom.png");
    }
    @Test void emailAloneCannotReplaceAnAlreadyLinkedProviderIdentity() {
        var users=mock(UserRepository.class);var service=new CustomOAuth2UserService(users,mock(NotificationService.class));
        var user=User.builder().email("person@gmail.com").provider("GOOGLE").providerId("original-subject").build();
        when(users.findByEmailWithRoles("person@gmail.com")).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.authenticate("google",provider(attributes("person@gmail.com",true,"different-subject"))))
                .isInstanceOf(OAuth2AuthenticationException.class);verify(users,never()).save(any());
    }
    @Test void thirdPartyEmailRequiresExplicitLinkWhileVerifiedWorkspaceCanLink() {
        var users=mock(UserRepository.class);var service=new CustomOAuth2UserService(users,mock(NotificationService.class));
        var user=User.builder().email("person@example.test").userType("STAFF").provider("LOCAL").build();
        when(users.findByEmailWithRoles("person@example.test")).thenReturn(Optional.of(user));
        var attributes=attributes("person@example.test",true,"workspace-subject");
        assertThatThrownBy(() -> service.authenticate("google",provider(attributes))).isInstanceOf(OAuth2AuthenticationException.class);
        attributes.put("hd","example.test");service.authenticate("google",provider(attributes));
        assertThat(user.getProviderId()).isEqualTo("workspace-subject");
    }
}
