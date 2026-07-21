package com.wemisson.career_camp.domain.participant.dto;

import com.wemisson.career_camp.domain.recruitment.dto.LectureType;

public record ParticipantLectureDraftResult(
	Long lectureId,
	LectureType lectureType,
	int remainingCapacity,
	boolean selected,
	boolean readyToFinalize
) {
}
