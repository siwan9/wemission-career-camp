package com.wemisson.career_camp.domain.participant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wemisson.career_camp.domain.participant.entity.ParticipantEntity;
import com.wemisson.career_camp.domain.participant.entity.ParticipantLectureEntity;
import com.wemisson.career_camp.domain.recruitment.entity.LectureEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;

public interface ParticipantLectureRepository extends JpaRepository<ParticipantLectureEntity, Long> {

	List<ParticipantLectureEntity> findByParticipantEntityIn(List<ParticipantEntity> participantEntities);

	Optional<ParticipantLectureEntity> findByParticipantEntity(ParticipantEntity participantEntity);

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
