package com.seal.hackathon.security;

import com.seal.hackathon.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implements cả UserDetails (dùng cho JWT filter) lẫn OAuth2User (dùng cho OAuth2 login).
 *
 * - Login email/password : new UserPrincipal(user)             → attributes = rỗng
 * - Login Google/GitHub  : new UserPrincipal(user, attributes) → attributes từ provider
 *
 * Authority assignment:
 * - FPT_STUDENT / EXTERNAL_STUDENT → ROLE_PARTICIPANT (từ user_type)
 * - STAFF có UserEventRole         → ROLE_EVENT_COORDINATOR / ROLE_MENTOR / ROLE_JUDGE
 */
@Getter
public class UserPrincipal implements UserDetails, OAuth2User {

    private final Integer userId;
    private final String email;
    private final String password;
    private final String userType;
    private final boolean approved;
    private final boolean isActive;
    private final Collection<? extends GrantedAuthority> authorities;

    // Chứa attributes từ Google/GitHub; rỗng với local login
    private final Map<String, Object> attributes;

    // ---------------------------------------------------------------
    // Constructor cho local login (email + password)
    // ---------------------------------------------------------------
    public UserPrincipal(User user) {
        this(user, Map.of());
    }

    // ---------------------------------------------------------------
    // Constructor cho OAuth2 login (Google / GitHub)
    // ---------------------------------------------------------------
    public UserPrincipal(User user, Map<String, Object> attributes) {
        this.userId      = user.getUserId();
        this.email       = user.getEmail();
        this.password    = user.getPasswordHash() != null ? user.getPasswordHash() : "";
        this.userType    = user.getUserType();
        this.approved    = Boolean.TRUE.equals(user.getIsApproved());
        this.isActive    = Boolean.TRUE.equals(user.getIsActive());
        this.authorities = buildAuthorities(user);
        this.attributes  = attributes;
    }

    // ---------------------------------------------------------------
    // UserDetails methods
    // ---------------------------------------------------------------

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return approved;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    // ---------------------------------------------------------------
    // OAuth2User methods
    // ---------------------------------------------------------------

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * OAuth2User.getName() — Spring dùng để xác định principal name trong OAuth2 flow.
     * Trả về email để nhất quán với getUsername().
     */
    @Override
    public String getName() {
        return email;
    }

    // ---------------------------------------------------------------
    // Authority builder
    // ---------------------------------------------------------------

    private static final Set<String> PARTICIPANT_TYPES = Set.of("FPT_STUDENT", "EXTERNAL_STUDENT");

    private List<GrantedAuthority> buildAuthorities(User user) {
        List<GrantedAuthority> list = new ArrayList<>();

        if (PARTICIPANT_TYPES.contains(user.getUserType())) {
            list.add(new SimpleGrantedAuthority("ROLE_PARTICIPANT"));
        }

        user.getUserEventRoles().stream()
                .map(uer -> new SimpleGrantedAuthority("ROLE_" + uer.getRole().getRoleName()))
                .distinct()
                .forEach(list::add);

        return list;
    }
}
