package com.wemisson.career_camp.domain.participant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wemisson.career_camp.domain.participant.entity.ParticipantEntity;
import com.wemisson.career_camp.domain.participant.entity.ParticipantLectureEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;

public interface ParticipantLectureRepository extends JpaRepository<ParticipantLectureEntity, Long> {

	List<ParticipantLectureEntity> findByParticipantEntityIn(List<ParticipantEntity> participantEntities);

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
}
