package com.example.github_event_capture.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.example.github_event_capture.service.impl.AuthServiceImpl;
import com.example.github_event_capture.entity.dto.UserDTO;


@SpringBootTest
public class AuthServiceTest {
    @Autowired
    private AuthServiceImpl authService;

    @Test
    public void testRegisration() {
        UserDTO user = new UserDTO();
        user.setEmail("test@example.com");
        user.setPassword("dy20010620");
        authService.userRegister(user);
    }

}
