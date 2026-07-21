package com.wemisson.career_camp.domain.participant.service.draft;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.wemisson.career_camp.domain.participant.repository.ParticipantLectureDraftRepository;
import com.wemisson.career_camp.domain.recruitment.entity.LectureEntity;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LectureCapacityService {

	private final ParticipantLectureDraftRepository participantLectureDraftRepository;

	public int getRemainingCapacity(LectureEntity lectureEntity, LocalDateTime now) {
		return lectureEntity.getMaxCapacity()
			- lectureEntity.getParticipantCount()
			- participantLectureDraftRepository.countByLectureEntityAndExpiresAtAfter(lectureEntity, now);
	}

	public void validateAvailable(LectureEntity lectureEntity, LocalDateTime now) {
		if (getRemainingCapacity(lectureEntity, now) <= 0) {
			throw new IllegalStateException("신청 가능한 자리가 없습니다.");
		}
	}
}
