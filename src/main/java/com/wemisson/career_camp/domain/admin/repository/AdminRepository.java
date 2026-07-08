package com.wemisson.career_camp.domain.admin.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wemisson.career_camp.domain.admin.entity.AdminEntity;

public interface AdminRepository extends JpaRepository<AdminEntity, Long> {

	Optional<AdminEntity> findByNameAndPassword(String name, String password);
}
