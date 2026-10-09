package com.bank.customer.repository;
import com.bank.customer.entity.EmployeeProfile; import org.springframework.data.domain.Page; import org.springframework.data.domain.Pageable; import org.springframework.data.jpa.repository.JpaRepository;
public interface EmployeeProfileRepository extends JpaRepository<EmployeeProfile,Integer> {
	Page<EmployeeProfile> findByFullNameContainingIgnoreCaseOrEmployeeCodeContainingIgnoreCase(String fullName, String employeeCode, Pageable pageable);
}