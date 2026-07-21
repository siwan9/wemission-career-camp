package com.wemisson.career_camp.domain.admin.service.auth;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wemisson.career_camp.domain.admin.entity.AdminEntity;
import com.wemisson.career_camp.domain.admin.repository.AdminRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

	private final AdminRepository adminRepository;

	@Transactional(readOnly = true)
	public Optional<AuthenticatedAdmin> authenticate(String name, String password) {
		return adminRepository.findByNameAndPassword(name, password)
			.map(adminEntity -> new AuthenticatedAdmin(
				adminEntity.getId(),
				adminEntity.getName()
			));
	}

	public record AuthenticatedAdmin(
		Long id,
		String name
	) {
	}
}
