package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.EmailAuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/auth")
public class EmailAuthController {
    @Resource private EmailAuthService auth;

    @PostMapping("/email/code")
    public Result code(@RequestBody EmailRequest request) { return auth.sendCode(request.email); }

    @PostMapping("/email/login")
    public Result emailLogin(@RequestBody EmailRequest request) { return auth.emailLogin(request.email, request.code); }

    @PostMapping("/password/login")
    public Result passwordLogin(@RequestBody EmailRequest request) { return auth.passwordLogin(request.email, request.password); }

    @PostMapping("/password/reset")
    public Result resetPassword(@RequestBody EmailRequest request) { return auth.resetPassword(request.email, request.code, request.password); }

    public static class EmailRequest {
        public String email;
        public String code;
        public String password;
    }
}
