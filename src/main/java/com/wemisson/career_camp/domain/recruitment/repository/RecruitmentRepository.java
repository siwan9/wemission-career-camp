package com.wemisson.career_camp.domain.recruitment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;

public interface RecruitmentRepository extends JpaRepository<RecruitmentEntity, Long> {

	@Query("""
		select r
		from RecruitmentEntity r
		where r.isOpen = true
		order by r.id desc
		""")
	List<RecruitmentEntity> findOpenRecruitments();

	List<RecruitmentEntity> findAllByOrderByIdDesc();
}
