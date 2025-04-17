package com.example.github_event_capture.security;

import com.example.github_event_capture.entity.User;
import org.springframework.stereotype.Service;
import com.example.github_event_capture.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.Authentication;

@Service
public class CustomUserDetailService implements UserDetailsService {
    private final UserRepository userRepository;

    public CustomUserDetailService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(username);

        if (user == null) {
            throw new UsernameNotFoundException(username);
        }

        return new CustomUserDetail(
                user.getId(),
                user.getEmail()
        );

    }

    public static Object getUidFromSecurityContext(Authentication auth) {
        return auth.getPrincipal();
    }
}
