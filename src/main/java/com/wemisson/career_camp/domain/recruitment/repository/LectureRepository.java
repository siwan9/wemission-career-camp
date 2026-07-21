package com.wemisson.career_camp.domain.recruitment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wemisson.career_camp.domain.recruitment.dto.LectureType;
import com.wemisson.career_camp.domain.recruitment.entity.LectureEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;

import jakarta.persistence.LockModeType;

public interface LectureRepository extends JpaRepository<LectureEntity, Long> {

	@Query("""
		select l
		from LectureEntity l
		where l.recruitmentEntity = :recruitmentEntity
			and l.type = :type
			and l.isOpen = true
		order by l.sortOrder asc, l.id asc
		""")
	List<LectureEntity> findOpenLectures(
		@Param("recruitmentEntity") RecruitmentEntity recruitmentEntity,
		@Param("type") LectureType type
	);

	@Query("""
		select l.id as id,
			l.isOpen as open,
			l.maxCapacity as maxCapacity,
			l.participantCount as participantCount
		from LectureEntity l
		where l.recruitmentEntity = :recruitmentEntity
		""")
	List<LectureAvailability> findLectureAvailabilities(
		@Param("recruitmentEntity") RecruitmentEntity recruitmentEntity
	);

	int countByRecruitmentEntity(RecruitmentEntity recruitmentEntity);

	List<LectureEntity> findByRecruitmentEntityOrderByTypeAscSortOrderAscIdAsc(RecruitmentEntity recruitmentEntity);

	List<LectureEntity> findByRecruitmentEntityAndTypeOrderBySortOrderAscIdAsc(
		RecruitmentEntity recruitmentEntity,
		LectureType type
	);

	boolean existsByRecruitmentEntityAndTypeAndIsOpenTrue(
		RecruitmentEntity recruitmentEntity,
		LectureType type
	);

	void deleteByRecruitmentEntity(RecruitmentEntity recruitmentEntity);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select l from LectureEntity l where l.id = :id")
	Optional<LectureEntity> findByIdForUpdate(@Param("id") Long id);

	interface LectureAvailability {
		Long getId();

		Boolean getOpen();

		Integer getMaxCapacity();

		Integer getParticipantCount();
	}
}
