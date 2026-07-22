package com.wemisson.career_camp.domain.recruitment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentParticipantTypeEntity;

import jakarta.persistence.LockModeType;

public interface RecruitmentParticipantTypeRepository extends JpaRepository<RecruitmentParticipantTypeEntity, Long> {

	@EntityGraph(attributePaths = "participantTypeEntity")
	List<RecruitmentParticipantTypeEntity> findByRecruitmentEntityOrderByIdAsc(RecruitmentEntity recruitmentEntity);

	Optional<RecruitmentParticipantTypeEntity> findByRecruitmentEntityAndParticipantTypeEntityId(
		RecruitmentEntity recruitmentEntity,
		Long participantTypeId
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select rule from RecruitmentParticipantTypeEntity rule where rule.id = :id")
	Optional<RecruitmentParticipantTypeEntity> findByIdForUpdate(@Param("id") Long id);

	void deleteByRecruitmentEntity(RecruitmentEntity recruitmentEntity);
}
