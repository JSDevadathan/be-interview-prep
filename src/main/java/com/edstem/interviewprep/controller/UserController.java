package com.edstem.interviewprep.controller;

import com.edstem.interviewprep.dto.UserResponse;
import com.edstem.interviewprep.service.UserService;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse profile(Principal principal) {
        return userService.getProfile(principal.getName());
    }

    @GetMapping
    public List<UserResponse> list() {
        return userService.listAll();
    }
}
