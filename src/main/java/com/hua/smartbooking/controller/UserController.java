package com.hua.smartbooking.controller;

import com.hua.smartbooking.model.User;
import com.hua.smartbooking.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/search")
    public ResponseEntity<List<User>> searchUsers(@RequestParam("q") String query) {
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.ok(new ArrayList<>());
        }
        List<User> users = userRepository.findByFullnameContainingIgnoreCaseOrEmailContainingIgnoreCase(query, query);
        return ResponseEntity.ok(users);
    }
}
