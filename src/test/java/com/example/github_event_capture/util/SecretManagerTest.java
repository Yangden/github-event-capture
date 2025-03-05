package com.example.github_event_capture.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import com.example.github_event_capture.utils.SecretManger;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentials;


public class SecretManagerTest {
    @Test
    @DisplayName("Print the secret key")
    public void printSecretKey() {
        String secret = SecretManger.GetSecretValue();
        System.out.println(secret);
    }


}
