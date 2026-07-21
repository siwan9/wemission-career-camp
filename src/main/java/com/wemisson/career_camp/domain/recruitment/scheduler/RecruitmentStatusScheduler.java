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

	private final RecruitmentRepository recruitmentRepository;
	private final RecruitmentQueryService recruitmentService;
	private final RecruitmentStatusPolicy recruitmentStatusPolicy;
	private final Clock clock;

	@Scheduled(fixedDelay = 60_000, initialDelay = 0)
	@Transactional
	public void synchronizeRecruitmentStatus() {
		LocalDateTime now = LocalDateTime.now(clock);
		List<RecruitmentEntity> recruitments = recruitmentRepository.findAllForUpdateOrderByIdAsc();

		for (RecruitmentEntity recruitment : recruitments) {
			if ((recruitment.isOpen() || recruitment.isWaiting()) && !now.isBefore(recruitment.getEndAt())) {
				recruitment.changeStatus(RecruitmentStatus.CLOSED);
				recruitmentService.evictRecruitmentCaches(recruitment.getId());
			}
		}

		if (recruitments.stream().anyMatch(RecruitmentEntity::isOpen)) {
			return;
		}

		for (RecruitmentEntity recruitment : recruitments) {
			if (!recruitment.isWaiting()
				|| now.isBefore(recruitment.getStartAt())
				|| !now.isBefore(recruitment.getEndAt())) {
				continue;
			}
			if (recruitmentStatusPolicy.canOpenAutomatically(recruitment)) {
				recruitment.changeStatus(RecruitmentStatus.OPEN);
				recruitmentService.evictRecruitmentCaches(recruitment.getId());
				return;
			}
		}
	}
}
