package com.wemisson.career_camp.domain.recruitment.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RecruitmentService {

	private final RecruitmentRepository recruitmentRepository;

	@Transactional(readOnly = true)
	public Optional<RecruitmentEntity> findCurrentRecruitment() {
		return recruitmentRepository.findOpenRecruitments()
			.stream()
			.findFirst();
	}
}
