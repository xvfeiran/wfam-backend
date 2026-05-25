package com.bosch.rbcc.aftermarketpartsmanagementsystem.repository;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.SmbConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SmbConfigurationRepository extends JpaRepository<SmbConfiguration, String> {
}
