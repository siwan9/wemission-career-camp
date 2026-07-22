package com.wemisson.career_camp.domain.recruitment.scheduler;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wemisson.career_camp.domain.recruitment.dto.RecruitmentStatus;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentRepository;
import com.wemisson.career_camp.domain.recruitment.service.policy.RecruitmentStatusPolicy;
import com.wemisson.career_camp.domain.recruitment.service.query.RecruitmentQueryService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RecruitmentStatusScheduler {

	private static final List<RecruitmentStatus> ACTIVE_RECRUITMENT_STATUSES = List.of(
		RecruitmentStatus.OPEN,
		RecruitmentStatus.WAITING
	);

	private final RecruitmentRepository recruitmentRepository;
	private final RecruitmentQueryService recruitmentService;
	private final RecruitmentStatusPolicy recruitmentStatusPolicy;
	private final Clock clock;

	@Scheduled(
		fixedRateString = "${career-camp.recruitment.status-sync-interval-ms:1000}",
		initialDelay = 0
	)
	@Transactional
	public void synchronizeRecruitmentStatus() {
		LocalDateTime now = LocalDateTime.now(clock);
		List<RecruitmentEntity> recruitments = recruitmentRepository.findByStatusInForUpdateOrderByIdAsc(
			ACTIVE_RECRUITMENT_STATUSES
		);

		for (RecruitmentEntity recruitment : recruitments) {
			if ((recruitment.isOpen() || recruitment.isWaiting())
				&& !now.isBefore(recruitment.getEndAt())
				&& recruitment.hasUnprocessedScheduleBoundary(recruitment.getEndAt())) {
				recruitment.changeStatus(RecruitmentStatus.CLOSED, now);
				recruitmentService.evictRecruitmentCaches(recruitment.getId());
			}
		}

		if (recruitments.stream().anyMatch(RecruitmentEntity::isOpen)) {
			return;
		}

		for (RecruitmentEntity recruitment : recruitments) {
			if (!recruitment.isWaiting()
				|| now.isBefore(recruitment.getStartAt())
				|| !now.isBefore(recruitment.getEndAt())
				|| !recruitment.hasUnprocessedScheduleBoundary(recruitment.getStartAt())) {
				continue;
			}
			if (recruitmentStatusPolicy.canOpenAutomatically(recruitment)) {
				recruitment.changeStatus(RecruitmentStatus.OPEN, now);
				recruitmentService.evictRecruitmentCaches(recruitment.getId());
				return;
			}
		}
	}
}
