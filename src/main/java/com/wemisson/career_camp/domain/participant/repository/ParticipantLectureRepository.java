package com.wemisson.career_camp.domain.participant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wemisson.career_camp.domain.participant.entity.ParticipantEntity;
import com.wemisson.career_camp.domain.participant.entity.ParticipantLectureEntity;
import com.wemisson.career_camp.domain.recruitment.entity.LectureEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;

import jakarta.persistence.LockModeType;

public interface ParticipantLectureRepository extends JpaRepository<ParticipantLectureEntity, Long> {

	@EntityGraph(attributePaths = {"participantEntity", "morningLectureEntity", "afternoonLectureEntity"})
	List<ParticipantLectureEntity> findByParticipantEntityIn(List<ParticipantEntity> participantEntities);

	Optional<ParticipantLectureEntity> findByParticipantEntity(ParticipantEntity participantEntity);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select pl from ParticipantLectureEntity pl where pl.id = :id")
	Optional<ParticipantLectureEntity> findByIdForUpdate(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select pl from ParticipantLectureEntity pl where pl.participantEntity = :participantEntity")
	Optional<ParticipantLectureEntity> findByParticipantEntityForUpdate(
		@Param("participantEntity") ParticipantEntity participantEntity
	);

	@Query("""
		select pl
		from ParticipantLectureEntity pl
			join fetch pl.participantEntity p
			join fetch p.recruitmentEntity
		where pl.id = :id
		""")
	Optional<ParticipantLectureEntity> findByIdWithRecruitment(@Param("id") Long id);

	@Query("""
		select pl
		from ParticipantLectureEntity pl
			join fetch pl.participantEntity p
			join fetch p.recruitmentEntity
			join fetch p.recruitmentChurchEntity
			join fetch p.participantTypeEntity
		where pl.id = :id
		""")
	Optional<ParticipantLectureEntity> findByIdWithLookupEditRelations(@Param("id") Long id);

	@Query("""
		select pl
		from ParticipantLectureEntity pl
			join fetch pl.participantEntity p
			join fetch p.recruitmentChurchEntity
			join fetch p.participantTypeEntity
		where pl.morningLectureEntity = :lectureEntity
		order by p.name asc
		""")
	List<ParticipantLectureEntity> findMorningApplicationsByLectureEntity(
		@Param("lectureEntity") LectureEntity lectureEntity
	);

	@Query("""
		select pl
		from ParticipantLectureEntity pl
			join fetch pl.participantEntity p
			join fetch p.recruitmentChurchEntity
			join fetch p.participantTypeEntity
		where pl.afternoonLectureEntity = :lectureEntity
		order by p.name asc
		""")
	List<ParticipantLectureEntity> findAfternoonApplicationsByLectureEntity(
		@Param("lectureEntity") LectureEntity lectureEntity
	);

	@Query("""
		select distinct pl
		from ParticipantLectureEntity pl
			join fetch pl.participantEntity p
			join fetch p.recruitmentChurchEntity
			join fetch p.participantTypeEntity
			left join fetch pl.morningLectureEntity
			left join fetch pl.afternoonLectureEntity
		where p.recruitmentEntity = :recruitmentEntity
			and (pl.morningLectureEntity is not null or pl.afternoonLectureEntity is not null)
		order by p.name asc
		""")
	List<ParticipantLectureEntity> findApplicationsByRecruitmentEntity(
		@Param("recruitmentEntity") RecruitmentEntity recruitmentEntity
	);

	@Query("""
		select count(pl)
		from ParticipantLectureEntity pl
		where pl.participantEntity.recruitmentEntity = :recruitmentEntity
			and pl.morningLectureEntity is not null
		""")
	int countMorningApplications(
		@Param("recruitmentEntity") RecruitmentEntity recruitmentEntity
	);

	@Query("""
		select count(pl)
		from ParticipantLectureEntity pl
		where pl.participantEntity.recruitmentEntity = :recruitmentEntity
			and pl.afternoonLectureEntity is not null
		""")
	int countAfternoonApplications(
		@Param("recruitmentEntity") RecruitmentEntity recruitmentEntity
	);

	void deleteByParticipantEntityRecruitmentEntity(RecruitmentEntity recruitmentEntity);
}
