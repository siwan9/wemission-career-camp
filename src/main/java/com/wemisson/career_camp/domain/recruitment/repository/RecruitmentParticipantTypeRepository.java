package com.wemisson.career_camp.domain.recruitment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentParticipantTypeEntity;

public interface RecruitmentParticipantTypeRepository extends JpaRepository<RecruitmentParticipantTypeEntity, Long> {

	@EntityGraph(attributePaths = "participantTypeEntity")
	List<RecruitmentParticipantTypeEntity> findByRecruitmentEntityOrderByIdAsc(RecruitmentEntity recruitmentEntity);

	Optional<RecruitmentParticipantTypeEntity> findByRecruitmentEntityAndParticipantTypeEntityId(
		RecruitmentEntity recruitmentEntity,
		Long participantTypeId
	);

	void deleteByRecruitmentEntity(RecruitmentEntity recruitmentEntity);
}
