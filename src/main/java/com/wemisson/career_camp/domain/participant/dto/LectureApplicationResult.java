package com.wemisson.career_camp.domain.participant.dto;

import com.wemisson.career_camp.domain.recruitment.dto.LectureType;

public record LectureApplicationResult(
	Long participantLectureId,
	Long lectureId,
	LectureType lectureType,
	int remainingCapacity
) {
}
