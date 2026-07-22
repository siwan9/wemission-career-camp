package com.wemisson.career_camp.domain.recruitment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wemisson.career_camp.domain.recruitment.dto.RecruitmentStatus;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;

import jakarta.persistence.LockModeType;

public interface RecruitmentRepository extends JpaRepository<RecruitmentEntity, Long> {

	List<RecruitmentEntity> findByStatusOrderByIdDesc(RecruitmentStatus status);

	List<RecruitmentEntity> findByStatusIn(List<RecruitmentStatus> statuses);

	List<RecruitmentEntity> findAllByOrderByIdDesc();

	boolean existsByStatus(RecruitmentStatus status);

	boolean existsByStatusAndIdNot(RecruitmentStatus status, Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from RecruitmentEntity r order by r.id asc")
	List<RecruitmentEntity> findAllForUpdateOrderByIdAsc();

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select r
		from RecruitmentEntity r
		where r.status in :statuses
		order by r.id asc
		""")
	List<RecruitmentEntity> findByStatusInForUpdateOrderByIdAsc(
		@Param("statuses") List<RecruitmentStatus> statuses
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from RecruitmentEntity r where r.id = :id")
	Optional<RecruitmentEntity> findByIdForUpdate(@Param("id") Long id);
}
