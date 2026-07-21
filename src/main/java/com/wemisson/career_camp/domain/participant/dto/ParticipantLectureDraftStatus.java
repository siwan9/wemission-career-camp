package com.wemisson.career_camp.domain.participant.dto;

import java.time.LocalDateTime;
import java.util.Map;

import com.wemisson.career_camp.domain.recruitment.dto.LectureType;

public record ParticipantLectureDraftStatus(
	Map<LectureType, SelectedLecture> selectedLectures,
	boolean readyToFinalize,
	LocalDateTime expiresAt
) {
	public record SelectedLecture(
		Long lectureId,
		int remainingCapacity,
		LocalDateTime expiresAt
	) {
	}
}
