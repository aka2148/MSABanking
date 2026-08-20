package com.bank.customer.controller;

import com.bank.customer.dto.CustomerProfileDto;
import com.bank.customer.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer")
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    @GetMapping("/profile")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'BANK_OFFICER', 'ADMIN')")
    public ResponseEntity<CustomerProfileDto> getMyProfile() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        CustomerProfileDto profile = customerService.getProfileByUsername(username);
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/profile")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'BANK_OFFICER', 'ADMIN')")
    public ResponseEntity<CustomerProfileDto> updateMyProfile(@RequestBody CustomerProfileDto profileDto) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        CustomerProfileDto updated = customerService.updateProfile(username, profileDto);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/profile/{username}")
    @PreAuthorize("hasAnyRole('BANK_OFFICER', 'ADMIN')")
    public ResponseEntity<CustomerProfileDto> getProfileByUsername(@PathVariable String username) {
        CustomerProfileDto profile = customerService.getProfileByUsername(username);
        return ResponseEntity.ok(profile);
    }
}
