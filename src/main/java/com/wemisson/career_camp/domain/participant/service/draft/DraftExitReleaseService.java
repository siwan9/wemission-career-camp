package com.wemisson.career_camp.domain.participant.service.draft;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import com.wemisson.career_camp.domain.participant.service.command.LectureApplicationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DraftExitReleaseService {

	private static final Duration RELOAD_GRACE_PERIOD = Duration.ofSeconds(3);

	private final TaskScheduler taskScheduler;
	private final LectureApplicationService lectureApplicationService;
	private final ConcurrentMap<String, UUID> pendingReleaseIds = new ConcurrentHashMap<>();

	public void scheduleRelease(String draftToken) {
		if (draftToken == null || draftToken.isBlank()) {
			return;
		}

		UUID releaseId = UUID.randomUUID();
		pendingReleaseIds.put(draftToken, releaseId);
		taskScheduler.schedule(
			() -> releaseIfStillPending(draftToken, releaseId),
			Instant.now().plus(RELOAD_GRACE_PERIOD)
		);
	}

	public void cancelRelease(String draftToken) {
		if (draftToken != null) {
			pendingReleaseIds.remove(draftToken);
		}
	}

	private void releaseIfStillPending(String draftToken, UUID releaseId) {
		if (!pendingReleaseIds.remove(draftToken, releaseId)) {
			return;
		}

		try {
			lectureApplicationService.releaseDrafts(draftToken);
		} catch (RuntimeException exception) {
			log.warn("페이지 이탈 임시점유 해제 실패. draftToken={}", draftToken, exception);
		}
	}
}
