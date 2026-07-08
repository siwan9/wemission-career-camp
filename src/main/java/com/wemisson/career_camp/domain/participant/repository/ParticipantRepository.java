package com.wemisson.career_camp.domain.participant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wemisson.career_camp.domain.participant.entity.ParticipantEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;

public interface ParticipantRepository extends JpaRepository<ParticipantEntity, Long> {

	int countByPhoneNumber(String phoneNumber);

	boolean existsByPhoneNumberAndPassword(
		String phoneNumber,
		String password
	);

	List<ParticipantEntity> findByPhoneNumberAndPassword(
		String phoneNumber,
		String password
	);

	int countByRecruitmentEntity(RecruitmentEntity recruitmentEntity);
}
