package com.wemisson.career_camp.domain.participant.dto;

import com.wemisson.career_camp.domain.participant.entity.ParticipantLectureEntity;
import com.wemisson.career_camp.domain.recruitment.entity.LectureEntity;

public record ParticipantLookupResult(
	Long participantId,
	Long participantLectureId,
	String participantName,
	ParticipantType participantType,
	String churchName,
	String morningLectureName,
	String afternoonLectureName
) {

	public static ParticipantLookupResult from(ParticipantLectureEntity participantLectureEntity) {
		return new ParticipantLookupResult(
			participantLectureEntity.getParticipantEntity().getId(),
			participantLectureEntity.getId(),
			participantLectureEntity.getParticipantEntity().getName(),
			participantLectureEntity.getParticipantEntity().getType().getType(),
			participantLectureEntity.getParticipantEntity().getRecruitmentChurchEntity().getName(),
			getLectureName(participantLectureEntity.getMorningLectureEntity()),
			getLectureName(participantLectureEntity.getAfternoonLectureEntity())
		);
	}

	private static String getLectureName(LectureEntity lectureEntity) {
		if (lectureEntity == null) {
			return "미신청";
		}

		return lectureEntity.getName();
	}
}
