package com.bank.customer.service;

import com.bank.common.exception.ResourceNotFoundException;
import com.bank.customer.dto.CustomerProfileDto;
import com.bank.customer.entity.CustomerProfile;
import com.bank.customer.repository.CustomerProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    @Autowired
    private CustomerProfileRepository customerProfileRepository;

    public CustomerProfileDto getProfileByUsername(String username) {
        CustomerProfile profile = customerProfileRepository.findByUserUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found for user: " + username));
        return mapToDto(profile);
    }

    public CustomerProfile getProfileEntityById(Long id) {
        return customerProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found with ID: " + id));
    }

    public CustomerProfile getProfileEntityByUsername(String username) {
        return customerProfileRepository.findByUserUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found for user: " + username));
    }

    @Transactional
    public CustomerProfileDto updateProfile(String username, CustomerProfileDto dto) {
        CustomerProfile profile = customerProfileRepository.findByUserUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found for user: " + username));

        profile.setFirstName(dto.getFirstName());
        profile.setLastName(dto.getLastName());
        profile.setPhone(dto.getPhone());
        profile.setAddress(dto.getAddress());
        if (dto.getCategory() != null) {
            profile.setCategory(dto.getCategory());
        }

        CustomerProfile updated = customerProfileRepository.save(profile);
        return mapToDto(updated);
    }

    private CustomerProfileDto mapToDto(CustomerProfile profile) {
        return CustomerProfileDto.builder()
                .id(profile.getId())
                .username(profile.getUser().getUsername())
                .email(profile.getUser().getEmail())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .phone(profile.getPhone())
                .address(profile.getAddress())
                .category(profile.getCategory())
                .build();
    }
}
