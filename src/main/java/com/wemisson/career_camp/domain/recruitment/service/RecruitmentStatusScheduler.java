package com.wemisson.career_camp.domain.recruitment.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RecruitmentStatusScheduler {

	private final RecruitmentRepository recruitmentRepository;
	private LocalDateTime lastCheckedAt;

	@Scheduled(fixedDelay = 60_000, initialDelay = 0)
	@Transactional
	public void synchronizeRecruitmentStatus() {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime checkedFrom = lastCheckedAt == null ? now.minusMinutes(1) : lastCheckedAt;
		List<RecruitmentEntity> recruitments = recruitmentRepository.findAllByOrderByIdDesc();

		for (RecruitmentEntity recruitment : recruitments) {
			if (isTriggered(recruitment.getEndAt(), checkedFrom, now)) {
				recruitment.changeOpen(false);
			}
		}

		boolean hasOpenRecruitment = recruitments.stream().anyMatch(RecruitmentEntity::isOpen);

		for (RecruitmentEntity recruitment : recruitments) {
			if (hasOpenRecruitment) {
				break;
			}

			if (isTriggered(recruitment.getStartAt(), checkedFrom, now)) {
				recruitment.changeOpen(true);
				hasOpenRecruitment = true;
			}
		}

		lastCheckedAt = now;
	}

	private boolean isTriggered(
		LocalDateTime targetAt,
		LocalDateTime checkedFrom,
		LocalDateTime now
	) {
		return targetAt.isAfter(checkedFrom)
			&& !targetAt.isAfter(now);
	}
}
