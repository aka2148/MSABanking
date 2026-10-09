package com.bank.customer.service;

import com.bank.common.exception.*;
import com.bank.customer.dto.CustomerResponse;
import com.bank.customer.entity.*;
import com.bank.customer.repository.*;
import com.bank.security.dto.*;
import com.bank.security.jwt.JwtTokenProvider;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class AuthCustomerServiceImpl implements AuthCustomerService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final CustomerProfileRepository customers;
    private final CustomerEmailRepository emails;
    private final RoleDefinitionRepository roles;
    private final PasswordEncoder encoder;
    private final JwtTokenProvider tokens;

    public AuthCustomerServiceImpl(
            AuthenticationManager authenticationManager,
            UserRepository users,
            CustomerProfileRepository customers,
            CustomerEmailRepository emails,
            RoleDefinitionRepository roles,
            PasswordEncoder encoder,
            JwtTokenProvider tokens) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.customers = customers;
        this.emails = emails;
        this.roles = roles;
        this.encoder = encoder;
        this.tokens = tokens;
    }

    // -------------------------------------------------------------------------
    // Auth
    // -------------------------------------------------------------------------

    private JwtResponse token(Authentication auth) {
        User user = (User) auth.getPrincipal();
        List<String> authorities = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList();
        return new JwtResponse(tokens.generateToken(auth), user.getUsername(), null, authorities);
    }

    @Override
    @Transactional
    public JwtResponse register(AuthRegisterRequest r) {
        if (users.existsByCredentialsUsername(r.getUsername())) {
            throw new InvalidInputException("Username is already taken");
        }

        // Create user + credentials
        User user = User.builder()
                .username(r.getUsername())
                .password(encoder.encode(r.getPassword()))
                .build();
        user.setUserType("CUSTOMER");

        // Assign default CUSTOMER role
        RoleDefinition role = roles.findByCode("ROLE_CUSTOMER").orElseGet(() -> {
            RoleDefinition x = new RoleDefinition();
            x.setCode("ROLE_CUSTOMER");
            x.setName("Customer");
            return roles.save(x);
        });
        UserRole assignment = new UserRole();
        assignment.setUser(user);
        assignment.setRole(role);
        user.getRoleAssignments().add(assignment);
        users.save(user);

        // Create customer profile
        CustomerProfile profile = new CustomerProfile();
        profile.setUser(user);
        profile.setCustomerNumber(
                "CUS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        profile.setLegalEntityType(r.getLegalEntityType());

        // Customer details (full name)
        CustomerDetails details = new CustomerDetails();
        details.setFullName(r.getFullName());
        profile.setDetails(details);
        customers.save(profile);

        // Persist the registration email into customer_emails
        // (users table has no email column — email lives in customer_emails)
        if (r.getEmail() != null && !r.getEmail().isBlank()) {
            CustomerEmail email = new CustomerEmail();
            email.setCustomer(profile);
            email.setEmailAddress(r.getEmail());
            email.setContactType("PRIMARY");
            email.setPrimarySignupEmail(true);
            email.setPrimaryEmail(true);
            emails.save(email);
        }

        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        return token(auth);
    }

    @Override
    public JwtResponse login(LoginRequest r) {
        return token(authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(r.getUsername(), r.getPassword())));
    }

    @Override
    public JwtResponse refresh(Authentication a) {
        return token(a);
    }

    @Override
    @Transactional
    public void changePassword(String username, ChangePasswordRequest r) {
        User u = users.findByCredentialsUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!encoder.matches(r.getCurrentPassword(), u.getPassword())) {
            throw new InvalidInputException("Current password is incorrect");
        }
        u.getCredentials().setPasswordHash(encoder.encode(r.getNewPassword()));
        users.save(u);
    }

    // -------------------------------------------------------------------------
    // Customer queries
    // -------------------------------------------------------------------------

    @Override
    public CustomerResponse me(String username) {
        return response(customers.findByUserCredentialsUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found")));
    }

    @Override
    public Page<CustomerResponse> customers(int page, int size, String status, String risk, String search) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1), Sort.by("id").ascending());
        // Normalise blank strings to null so the JPQL IS NULL check skips the filter
        String s = (status != null && !status.isBlank()) ? status : null;
        String r = (risk   != null && !risk.isBlank())   ? risk   : null;
        String q = (search != null && !search.isBlank()) ? search : null;
        return customers.findWithFilters(s, r, q, pageable).map(this::response);
    }

    @Override
    public CustomerResponse getCustomer(Long id) {
        return response(customers.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found")));
    }

    @Override
    public CustomerResponse getCustomerByUserId(Long userId) {
        return response(customers.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found for user")));
    }

    @Override
    @Transactional
    public CustomerProfile updateStatus(Long id, String status, String risk) {
        CustomerProfile c = customers.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        if (status != null) c.setKycStatus(status);
        if (risk   != null) c.setRiskCategory(risk);
        return customers.save(c);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private CustomerResponse response(CustomerProfile c) {
        return new CustomerResponse(
                c.getId(),
                c.getCustomerNumber(),
                c.getUser().getUsername(),
                c.getDetails() == null ? null : c.getDetails().getFullName(),
                c.getKycStatus(),
                c.getRiskCategory(),
                c.getLegalEntityType());
    }
}