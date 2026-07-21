package com.wemisson.career_camp.domain.participant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wemisson.career_camp.domain.participant.entity.ParticipantEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentChurchEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;

public interface ParticipantRepository extends JpaRepository<ParticipantEntity, Long> {

	int countByRecruitmentEntity(RecruitmentEntity recruitmentEntity);

	int countByRecruitmentChurchEntity(RecruitmentChurchEntity recruitmentChurchEntity);

	@Query("""
		select p.recruitmentEntity.id as recruitmentId,
			count(p) as participantCount
		from ParticipantEntity p
		where p.recruitmentEntity in :recruitmentEntities
		group by p.recruitmentEntity.id
		""")
	List<RecruitmentParticipantCount> countByRecruitmentEntities(
		@Param("recruitmentEntities") List<RecruitmentEntity> recruitmentEntities
	);

	@Query("""
		select p.recruitmentChurchEntity.id as churchId,
			count(p) as participantCount
		from ParticipantEntity p
		where p.recruitmentChurchEntity in :churches
		group by p.recruitmentChurchEntity.id
		""")
	List<ChurchParticipantCount> countByRecruitmentChurchEntities(
		@Param("churches") List<RecruitmentChurchEntity> churches
	);

	@EntityGraph(attributePaths = {"participantTypeEntity", "recruitmentChurchEntity"})
	List<ParticipantEntity> findByRecruitmentEntityOrderByNameAsc(RecruitmentEntity recruitmentEntity);

	@EntityGraph(attributePaths = {"participantTypeEntity", "recruitmentChurchEntity"})
	Page<ParticipantEntity> findByRecruitmentEntityOrderByIdAsc(
		RecruitmentEntity recruitmentEntity,
		Pageable pageable
	);

	@EntityGraph(attributePaths = {"participantTypeEntity", "recruitmentChurchEntity"})
	List<ParticipantEntity> findByRecruitmentChurchEntityOrderByNameAsc(RecruitmentChurchEntity recruitmentChurchEntity);

	@EntityGraph(attributePaths = {"participantTypeEntity", "recruitmentChurchEntity"})
	List<ParticipantEntity> findByRecruitmentEntityAndPhoneNumberContainingOrderByNameAsc(
		RecruitmentEntity recruitmentEntity,
		String phoneNumber
	);

	@EntityGraph(attributePaths = {"participantTypeEntity", "recruitmentChurchEntity"})
	List<ParticipantEntity> findByRecruitmentEntityAndNameAndRecruitmentChurchEntityIdAndPhoneNumberOrderByIdAsc(
		RecruitmentEntity recruitmentEntity,
		String name,
		Long recruitmentChurchId,
		String phoneNumber
	);

	@Query("""
		select p
		from ParticipantEntity p
			join fetch p.recruitmentEntity
			join fetch p.participantTypeEntity
			join fetch p.recruitmentChurchEntity
		where p.id = :id
		""")
	Optional<ParticipantEntity> findByIdWithEditRelations(@Param("id") Long id);

	void deleteByRecruitmentEntity(RecruitmentEntity recruitmentEntity);

	interface RecruitmentParticipantCount {
		Long getRecruitmentId();

		Long getParticipantCount();
	}

	interface ChurchParticipantCount {
		Long getChurchId();

		Long getParticipantCount();
	}
}
