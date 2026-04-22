package com.acme.PayNotify.repository;

import com.acme.PayNotify.entity.EnterpriseMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EnterpriseMasterRepository extends JpaRepository<EnterpriseMaster, Long> {

    Optional<EnterpriseMaster> findByEnterpriseCode(String enterpriseCode);

    boolean existsByEnterpriseCode(String enterpriseCode);
}