package com.example.github_event_capture.service.impl;

import com.example.github_event_capture.repository.UserRepository;
import com.example.github_event_capture.utils.PasswordUtil;
import io.jsonwebtoken.Jwt;
import org.springframework.stereotype.Service;
import com.example.github_event_capture.entity.dto.UserDTO;
import com.example.github_event_capture.entity.User;
import com.example.github_event_capture.utils.Result;
import com.example.github_event_capture.utils.HttpResponseMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AuthServiceImpl {
    private final UserRepository userRepository;
    private final Logger LOGGER = LoggerFactory.getLogger(AuthServiceImpl.class);
    private final MonitorServiceImpl monitorService;
    private final JwtService jwtService;

    public AuthServiceImpl(UserRepository userRepository, MonitorServiceImpl monitorService, JwtService jwtService)
    {
        this.userRepository = userRepository;
        this.monitorService = monitorService;
        this.jwtService = jwtService;
    }


    public Result userRegister(UserDTO userInfo) {
        /* corner case: duplicate registered email */
        if (userRepository.findByEmail(userInfo.getEmail()) != null) {
            return Result.fail(HttpResponseMsg.DUPLICATE_EMAIL);
        }
        /* initiate User class object for crud */
        User user = new User();
        user.setEmail(userInfo.getEmail());
        user.setPassword(PasswordUtil.encryptPassword(userInfo.getPassword()));
        /* write to the database */
        userRepository.save(user);

        return Result.success(HttpResponseMsg.OK);

    }

    // user login
    public Result<String> userLogin(UserDTO userInfo) {
        User user = userRepository.findByEmail(userInfo.getEmail());
        /* check if registered */
        if (user == null) {
            return Result.fail(HttpResponseMsg.NOT_REGISTERED);
        }

        /* check if password matched */
        if (!PasswordUtil.Validate(userInfo.getPassword(), user.getPassword())) {
            LOGGER.error("password validation failed");
            return Result.fail(HttpResponseMsg.INCORRECT_PASSWORD);
        }

        /* grant jwt token: login success */
        String jws = jwtService.generateToken(user.getId().toString(), user.getEmail());
        return Result.success(HttpResponseMsg.OK, jws);
    }


}
