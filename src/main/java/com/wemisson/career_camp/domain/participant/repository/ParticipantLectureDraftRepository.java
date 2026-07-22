package com.wemisson.career_camp.domain.participant.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wemisson.career_camp.domain.participant.entity.ParticipantLectureDraftEntity;
import com.wemisson.career_camp.domain.participant.entity.ParticipantEntity;
import com.wemisson.career_camp.domain.recruitment.dto.LectureType;
import com.wemisson.career_camp.domain.recruitment.entity.LectureEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;

import jakarta.persistence.LockModeType;

public interface ParticipantLectureDraftRepository extends JpaRepository<ParticipantLectureDraftEntity, Long> {

	List<ParticipantLectureDraftEntity> findByDraftTokenAndExpiresAtAfter(
		String draftToken,
		LocalDateTime now
	);

	Optional<ParticipantLectureDraftEntity> findByDraftTokenAndLectureType(
		String draftToken,
		LectureType lectureType
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select d
		from ParticipantLectureDraftEntity d
		where d.draftToken = :draftToken
		order by d.id asc
		""")
	List<ParticipantLectureDraftEntity> findByDraftTokenForUpdate(
		@Param("draftToken") String draftToken
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select d
		from ParticipantLectureDraftEntity d
		where d.participantEntity = :participantEntity
		order by d.id asc
		""")
	List<ParticipantLectureDraftEntity> findByParticipantEntityForUpdate(
		@Param("participantEntity") ParticipantEntity participantEntity
	);

	int countByLectureEntityAndExpiresAtAfter(
		LectureEntity lectureEntity,
		LocalDateTime now
	);

	void deleteByDraftToken(String draftToken);

	void deleteByParticipantEntity(ParticipantEntity participantEntity);

	void deleteByRecruitmentEntity(RecruitmentEntity recruitmentEntity);

	void deleteByExpiresAtBefore(LocalDateTime now);

}
