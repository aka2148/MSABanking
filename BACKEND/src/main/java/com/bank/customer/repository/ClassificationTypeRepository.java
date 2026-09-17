package com.bank.customer.repository;
import com.bank.customer.entity.ClassificationType; import org.springframework.data.jpa.repository.JpaRepository; import java.util.Optional;
public interface ClassificationTypeRepository extends JpaRepository<ClassificationType,Integer> { Optional<ClassificationType> findByCode(String code); }