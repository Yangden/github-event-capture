package com.example.github_event_capture.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/* use bcrypt to encode the password */
@Component
public class PasswordUtil {
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public static String encryptPassword(String password) {
        return encoder.encode(password);
    }

    public static boolean Validate(String toBeValidated, String storedPassword) {
        return encoder.matches(toBeValidated, storedPassword);
    }


}
