package com.example.github_event_capture.security;


import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.example.github_event_capture.security.CustomUserDetail;


@Service
public class SecurityContextService {

    public static long getUidFromSeucrityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return ((CustomUserDetail) authentication.getPrincipal()).getUid();
    }
}
