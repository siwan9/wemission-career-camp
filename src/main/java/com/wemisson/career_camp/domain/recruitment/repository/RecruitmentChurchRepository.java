package com.wemisson.career_camp.domain.recruitment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentChurchEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;

public interface RecruitmentChurchRepository extends JpaRepository<RecruitmentChurchEntity, Long> {

	List<RecruitmentChurchEntity> findByRecruitmentEntityOrderByNameAsc(RecruitmentEntity recruitmentEntity);

	Optional<RecruitmentChurchEntity> findByIdAndRecruitmentEntity(Long id, RecruitmentEntity recruitmentEntity);
}
