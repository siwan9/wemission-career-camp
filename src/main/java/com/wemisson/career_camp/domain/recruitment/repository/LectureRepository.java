package com.wemisson.career_camp.domain.recruitment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wemisson.career_camp.domain.recruitment.dto.LectureType;
import com.wemisson.career_camp.domain.recruitment.entity.LectureEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;

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

	int countByRecruitmentEntity(RecruitmentEntity recruitmentEntity);

	List<LectureEntity> findByRecruitmentEntityOrderByTypeAscSortOrderAscIdAsc(RecruitmentEntity recruitmentEntity);

	List<LectureEntity> findByRecruitmentEntityAndTypeOrderBySortOrderAscIdAsc(
		RecruitmentEntity recruitmentEntity,
		LectureType type
	);

	void deleteByRecruitmentEntity(RecruitmentEntity recruitmentEntity);
}
