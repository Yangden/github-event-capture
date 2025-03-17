package com.example.github_event_capture.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import com.example.github_event_capture.utils.SecretManger;

public class SecretManagerTest {
    @Test
    @DisplayName("Print the secret key")
    public void printSecretKey() {
        String secret = SecretManger.GetSecretValue();
        System.out.println(secret);
    }


}
