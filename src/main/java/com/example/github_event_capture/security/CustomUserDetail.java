package com.example.github_event_capture.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.GrantedAuthority;
import java.util.Collection;
import java.util.List;

public class CustomUserDetail implements UserDetails {
    private long uid;
    private String username;
    private String password;


    public CustomUserDetail(long uid, String username) {
        this.uid = uid;
        this.username = username;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    public long getUid() {
        return uid;
    }





}
