package com.wemisson.career_camp.domain.recruitment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentParticipantType;

public interface RecruitmentParticipantTypeRepository extends JpaRepository<RecruitmentParticipantType, Long> {

	List<RecruitmentParticipantType> findByRecruitmentEntityOrderByIdAsc(RecruitmentEntity recruitmentEntity);

	Optional<RecruitmentParticipantType> findByRecruitmentEntityAndParticipantTypeEntityId(
		RecruitmentEntity recruitmentEntity,
		Long participantTypeId
	);
}
