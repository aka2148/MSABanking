package com.bank.security.bootstrap;

import com.bank.common.exception.InvalidInputException;
import com.bank.customer.entity.EmployeeProfile;
import com.bank.customer.entity.RoleDefinition;
import com.bank.customer.entity.User;
import com.bank.customer.repository.EmployeeProfileRepository;
import com.bank.customer.repository.RoleDefinitionRepository;
import com.bank.customer.repository.UserRepository;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaffBootstrapServiceTest {
    @Test
    void employeeEmailUsesTheForwardSchemaMigration() throws Exception {
        assertEquals("emp_email", EmployeeProfile.class.getDeclaredField("email")
                .getAnnotation(Column.class).name());
        String migration = new String(getClass().getResourceAsStream(
                "/db/migration/V21__employee_profile_email.sql").readAllBytes());
        assertNotNull(migration);
        assertTrue(migration.contains("emp_email"));
    }

    @Test
    void createsOnlyBankOfficerAndStoresBcryptHashAndEmployeeProfile() {
        UserRepository users = mock(UserRepository.class);
        RoleDefinitionRepository roles = mock(RoleDefinitionRepository.class);
        EmployeeProfileRepository employees = mock(EmployeeProfileRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        StaffBootstrapService service = new StaffBootstrapService(users, roles, employees, encoder);
        String username = "staff-" + UUID.randomUUID();
        String password = UUID.randomUUID() + "-generated-test-password";
        String email = "officer-" + UUID.randomUUID() + "@example.test";
        when(users.findByCredentialsUsername(username)).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(81L);
            return saved;
        });
        when(roles.findByCode("ROLE_BANK_OFFICER")).thenReturn(Optional.of(bankOfficerRole()));
        when(employees.save(any(EmployeeProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StaffBootstrapService.BootstrapResult result = service.createBankOfficer(
                username, password, "Test Bank Officer", email);

        assertTrue(result.created());
        assertEquals(81L, result.userId());
        var capturedUser = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).save(capturedUser.capture());
        User saved = capturedUser.getValue();
        assertEquals("EMPLOYEE", saved.getUserType());
        assertEquals("ROLE_BANK_OFFICER", saved.getAuthorities().iterator().next().getAuthority());
        assertNotEquals(password, saved.getPassword());
        assertTrue(encoder.matches(password, saved.getPassword()));
        var capturedEmployee = org.mockito.ArgumentCaptor.forClass(EmployeeProfile.class);
        verify(employees).save(capturedEmployee.capture());
        assertEquals("Test Bank Officer", capturedEmployee.getValue().getFullName());
        assertEquals(email, capturedEmployee.getValue().getEmail());
    }

    @Test
    void refusesToPromoteAnExistingCustomerAndDoesNotChangeItsRole() {
        UserRepository users = mock(UserRepository.class);
        RoleDefinitionRepository roles = mock(RoleDefinitionRepository.class);
        EmployeeProfileRepository employees = mock(EmployeeProfileRepository.class);
        StaffBootstrapService service = new StaffBootstrapService(users, roles, employees,
                new BCryptPasswordEncoder());
        String username = "staff-" + UUID.randomUUID();
        String password = UUID.randomUUID() + "-generated-test-password";
        String email = "officer-" + UUID.randomUUID() + "@example.test";
        User customer = User.builder().username(username).password(UUID.randomUUID().toString()).build();
        when(users.findByCredentialsUsername(username)).thenReturn(Optional.of(customer));

        assertThrows(InvalidInputException.class, () -> service.createBankOfficer(
                username, password, "Test Bank Officer", email));

        verify(users, never()).save(any(User.class));
        verify(employees, never()).save(any(EmployeeProfile.class));
        verify(roles, never()).save(any(RoleDefinition.class));
    }

    private static RoleDefinition bankOfficerRole() {
        RoleDefinition role = new RoleDefinition();
        role.setCode("ROLE_BANK_OFFICER");
        role.setName("Bank Officer");
        return role;
    }
}
