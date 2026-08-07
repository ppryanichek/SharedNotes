package com.eugene.sharednotes.security;

import com.eugene.sharednotes.common.Role;
import com.eugene.sharednotes.user.UserEntity;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@NullMarked
@RequiredArgsConstructor
public class SecurityUser implements UserDetails {
    private final UserEntity user;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() { return user.getPassword(); }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    public Long getUserId() { return user.getId(); }

    public Role getRole() { return user.getRole(); }

    // TODO: переопределить, когда добавится фукционал банов и удаления пользователя
    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return UserDetails.super.isEnabled();
    }
}
