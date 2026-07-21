package com.wemisson.career_camp.domain.participant.scheduler;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wemisson.career_camp.domain.participant.repository.ParticipantLectureDraftRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ParticipantLectureDraftCleanupScheduler {

	private final ParticipantLectureDraftRepository participantLectureDraftRepository;
	private final Clock clock;

	@Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
	@Transactional
	public void deleteExpiredDrafts() {
		participantLectureDraftRepository.deleteByExpiresAtBefore(LocalDateTime.now(clock));
	}
}
