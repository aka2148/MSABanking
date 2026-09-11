package com.bank.product.repository;
import com.bank.product.entity.ProductRoleConfig; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface ProductRoleConfigRepository extends JpaRepository<ProductRoleConfig,Integer>{List<ProductRoleConfig> findByProductId(Integer productId); Optional<ProductRoleConfig> findByProductIdAndRoleTypeId(Integer productId,Integer roleTypeId); boolean existsByProductIdAndRoleTypeId(Integer productId,Integer roleTypeId);}
