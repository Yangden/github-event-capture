package com.example.github_event_capture.controller;

import com.example.github_event_capture.service.impl.AuthServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.github_event_capture.entity.dto.UserDTO;
import com.example.github_event_capture.utils.Result;
import com.example.github_event_capture.utils.HttpResponseMsg;
import org.springframework.http.HttpStatus;

@RestController(value = "auth")
public class AuthController {
    private final AuthServiceImpl authService;

    public AuthController(AuthServiceImpl authService) {
        this.authService = authService;
    }

    /* register */
    @PostMapping(value = "register")
    public ResponseEntity<String> register(@RequestBody UserDTO user) {
        /* how to handle invalid url */
        authService.userRegister(user);
        return ResponseEntity.ok("User registered successfully");
    }

    /* login */
    @PostMapping(value = "login")
    public ResponseEntity<String> login(@RequestBody UserDTO user) {
        Result result = authService.userLogin(user);
        if (result.isSuccess()) { // login successful
            return ResponseEntity.ok(result.getData().toString());
        } else if (result.getMessage().equals(HttpResponseMsg.NOT_REGISTERED)) { // not register
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } else { // password incorrect
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
    }




}
