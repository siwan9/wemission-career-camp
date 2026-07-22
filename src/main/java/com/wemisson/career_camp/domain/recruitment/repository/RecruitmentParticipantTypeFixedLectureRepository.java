package com.wemisson.career_camp.domain.recruitment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wemisson.career_camp.domain.recruitment.entity.LectureEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentParticipantTypeEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentParticipantTypeFixedLectureEntity;

public interface RecruitmentParticipantTypeFixedLectureRepository
	extends JpaRepository<RecruitmentParticipantTypeFixedLectureEntity, Long> {

	List<RecruitmentParticipantTypeFixedLectureEntity>
		findByRecruitmentParticipantTypeEntityOrderByLectureTypeAscIdAsc(
			RecruitmentParticipantTypeEntity recruitmentParticipantTypeEntity
		);

	@Query("""
		select fixedLecture
		from RecruitmentParticipantTypeFixedLectureEntity fixedLecture
		join fetch fixedLecture.recruitmentParticipantTypeEntity rule
		join fetch rule.participantTypeEntity participantType
		join fetch fixedLecture.lectureEntity lecture
		where rule.recruitmentEntity = :recruitmentEntity
		order by rule.id asc, fixedLecture.lectureType asc, fixedLecture.id asc
		""")
	List<RecruitmentParticipantTypeFixedLectureEntity> findByRecruitmentEntityWithRelations(
		@Param("recruitmentEntity") RecruitmentEntity recruitmentEntity
	);

	boolean existsByLectureEntity(LectureEntity lectureEntity);

	@Modifying
	@Query("""
		delete from RecruitmentParticipantTypeFixedLectureEntity fixedLecture
		where fixedLecture.recruitmentParticipantTypeEntity.recruitmentEntity = :recruitmentEntity
		""")
	void deleteByRecruitmentEntity(@Param("recruitmentEntity") RecruitmentEntity recruitmentEntity);
}
