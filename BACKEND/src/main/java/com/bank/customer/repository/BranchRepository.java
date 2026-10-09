package com.bank.customer.repository;
import com.bank.customer.entity.Branch; import org.springframework.data.jpa.repository.JpaRepository;
public interface BranchRepository extends JpaRepository<Branch,Long> {
    default java.util.Optional<Branch> findById(Integer id) { return findById(id.longValue()); }
}
