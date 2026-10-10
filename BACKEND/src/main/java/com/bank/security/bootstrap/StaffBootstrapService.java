package com.bank.security.bootstrap;

import com.bank.common.exception.InvalidInputException;
import com.bank.customer.entity.EmployeeProfile;
import com.bank.customer.entity.RoleDefinition;
import com.bank.customer.entity.User;
import com.bank.customer.entity.UserRole;
import com.bank.customer.repository.EmployeeProfileRepository;
import com.bank.customer.repository.RoleDefinitionRepository;
import com.bank.customer.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/** Creates only the first configured BANK_OFFICER account; never promotes an existing user. */
@Service
public class StaffBootstrapService {
    private static final String BANK_OFFICER = "ROLE_BANK_OFFICER";

    private final UserRepository users;
    private final RoleDefinitionRepository roles;
    private final EmployeeProfileRepository employees;
    private final PasswordEncoder passwordEncoder;

    public StaffBootstrapService(UserRepository users, RoleDefinitionRepository roles,
                                 EmployeeProfileRepository employees, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.roles = roles;
        this.employees = employees;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public BootstrapResult createBankOfficer(String username, String password, String fullName, String email) {
        String login = required(username, "username");
        String name = required(fullName, "full name");
        String address = required(email, "email");
        if (!address.contains("@")) throw new InvalidInputException("Bootstrap email must be valid");
        if (password == null || password.length() < 8) {
            throw new InvalidInputException("Bootstrap password must contain at least 8 characters");
        }

        var existing = users.findByCredentialsUsername(login);
        if (existing.isPresent()) {
            boolean alreadyStaff = existing.get().getAuthorities().stream()
                    .anyMatch(authority -> BANK_OFFICER.equals(authority.getAuthority()));
            if (alreadyStaff) return new BootstrapResult(existing.get().getId(), login, false);
            throw new InvalidInputException("Username already exists without the BANK_OFFICER role; use an ADMIN role-assignment flow");
        }

        RoleDefinition role = roles.findByCode(BANK_OFFICER).orElseGet(() -> {
            RoleDefinition created = new RoleDefinition();
            created.setCode(BANK_OFFICER);
            created.setName("Bank Officer");
            created.setDescription("Staff access for FD product setup and operations");
            return roles.save(created);
        });

        User user = User.builder()
                .username(login)
                .password(passwordEncoder.encode(password))
                .build();
        user.setUserType("EMPLOYEE");
        user.setStatus("ACTIVE");
        UserRole assignment = new UserRole();
        assignment.setRole(role);
        user.addRoleAssignment(assignment);
        User saved = users.save(user);

        EmployeeProfile employee = new EmployeeProfile();
        employee.setUser(saved);
        employee.setEmployeeCode("EMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        employee.setFullName(name);
        employee.setEmail(address);
        employee.setDesignation("BANK_OFFICER");
        employee.setDateOfJoining(LocalDate.now(ZoneOffset.UTC));
        employees.save(employee);

        return new BootstrapResult(saved.getId(), login, true);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new InvalidInputException("Bootstrap " + field + " is required");
        return value.trim();
    }

    public record BootstrapResult(Long userId, String username, boolean created) { }
}
