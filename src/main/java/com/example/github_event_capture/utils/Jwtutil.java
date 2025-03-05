package com.example.github_event_capture.utils;

import io.jsonwebtoken.*;
import java.security.Key;
import com.example.github_event_capture.utils.SecretManger;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Jwtutil {
    private static final String SECRET = SecretManger.GetSecretValue();
    private static Logger LOGGER = LoggerFactory.getLogger(Jwtutil.class);

    /* generate token */
    public static String generateToken(String uid, String email) {
        String jws = Jwts.builder()
                .subject(email)
                .id(uid)
                .signWith(getSignedKey())
                .compact();
        return jws;
    }

    /* get jwt claims */
    private static Claims extractClaims(String token) {
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
    public static String extractEmail(String token) {
        Claims jws = extractClaims(token);
        if (jws == null) {
            return null;
        }
        return jws.getSubject();
    }

    /* get signed key */
    private static SecretKey getSignedKey() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
