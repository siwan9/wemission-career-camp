package com.wemisson.career_camp.domain.participant.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.wemisson.career_camp.domain.participant.entity.ParticipantEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentChurchEntity;
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

	int countByRecruitmentChurchEntity(RecruitmentChurchEntity recruitmentChurchEntity);

	List<ParticipantEntity> findByRecruitmentEntityOrderByNameAsc(RecruitmentEntity recruitmentEntity);

	Page<ParticipantEntity> findByRecruitmentEntityOrderByIdAsc(
		RecruitmentEntity recruitmentEntity,
		Pageable pageable
	);

	List<ParticipantEntity> findByRecruitmentChurchEntityOrderByNameAsc(RecruitmentChurchEntity recruitmentChurchEntity);

	List<ParticipantEntity> findByRecruitmentEntityAndPhoneNumberContainingOrderByNameAsc(
		RecruitmentEntity recruitmentEntity,
		String phoneNumber
	);

	void deleteByRecruitmentEntity(RecruitmentEntity recruitmentEntity);
}
