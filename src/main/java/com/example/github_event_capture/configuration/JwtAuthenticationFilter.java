package com.example.github_event_capture.configuration;

import com.example.github_event_capture.service.impl.JwtService;
import jakarta.servlet.FilterChain;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import com.example.github_event_capture.repository.UserRepository;
import com.example.github_event_capture.security.CustomUserDetailService;
import org.springframework.security.core.userdetails.UserDetails;


@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final UserRepository userRepository;
    private final CustomUserDetailService customUserDetailService;
    private final JwtService jwtService;

    public JwtAuthenticationFilter(UserRepository userRepository,
                                   CustomUserDetailService customUserDetailService,
                                   JwtService jwtService) {
        this.userRepository = userRepository;
        this.customUserDetailService = customUserDetailService;
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String jws = authHeader.substring(7); // get signed jwt token
            String userEmail = jwtService.extractEmail(jws);

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            /* start to verify jwt token */
            if (userEmail != null && authentication == null) {
                System.out.println(userEmail);
                UserDetails userDetails = customUserDetailService.loadUserByUsername(userEmail);
                if (userDetails.getUsername().equals(userEmail)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}