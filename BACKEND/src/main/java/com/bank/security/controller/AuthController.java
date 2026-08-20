package com.bank.security.controller;

import com.bank.common.exception.InvalidInputException;
import com.bank.customer.entity.CustomerCategory;
import com.bank.customer.entity.CustomerProfile;
import com.bank.customer.entity.Role;
import com.bank.customer.entity.User;
import com.bank.customer.repository.CustomerProfileRepository;
import com.bank.customer.repository.UserRepository;
import com.bank.security.dto.JwtResponse;
import com.bank.security.dto.LoginRequest;
import com.bank.security.dto.RegisterRequest;
import com.bank.security.jwt.JwtTokenProvider;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerProfileRepository customerProfileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtTokenProvider.generateToken(authentication);

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByUsername(userDetails.getUsername()).orElseThrow();

        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new JwtResponse(jwt, userDetails.getUsername(), user.getEmail(), roles));
    }

    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new InvalidInputException("Error: Username is already taken!");
        }

        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new InvalidInputException("Error: Email is already in use!");
        }

        Role role = registerRequest.getRole() != null ? registerRequest.getRole() : Role.ROLE_CUSTOMER;

        User user = User.builder()
                .username(registerRequest.getUsername())
                .email(registerRequest.getEmail())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .role(role)
                .build();

        CustomerCategory category = registerRequest.getCategory() != null ? registerRequest.getCategory() : CustomerCategory.STANDARD;

        CustomerProfile profile = CustomerProfile.builder()
                .user(user)
                .firstName(registerRequest.getFirstName())
                .lastName(registerRequest.getLastName())
                .phone(registerRequest.getPhone())
                .address(registerRequest.getAddress())
                .category(category)
                .build();

        customerProfileRepository.save(profile);

        return ResponseEntity.ok("User registered successfully with profile!");
    }
}
