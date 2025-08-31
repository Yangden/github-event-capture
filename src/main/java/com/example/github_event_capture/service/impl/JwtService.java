package com.example.github_event_capture.service.impl;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretManager secretManager;
    private final String SECRET;
    private static Logger LOGGER = LoggerFactory.getLogger(JwtService.class);

    public JwtService(SecretManager secretManager) {
        this.secretManager = secretManager;
        this.SECRET = secretManager.GetSecretValue();
    }
    /* generate token */
    public String generateToken(String uid, String email) {
        String jws = Jwts.builder()
                .subject(email)
                .id(uid)
                .signWith(getSignedKey())
                .compact();
        return jws;
    }

    /* get jwt claims */
    private  Claims extractClaims(String token) {
        try {
            Claims jws = Jwts.parser()
                    .verifyWith(getSignedKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return jws;
        } catch (JwtException e) {
            LOGGER.error(e.getMessage());
            return null;
        }

    }
    /* get user email for verification */
    public String extractEmail(String token) {
        Claims jws = extractClaims(token);
        if (jws == null) {
            return null;
        }
        return jws.getSubject();
    }

    /* get signed key */
    private SecretKey getSignedKey() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
