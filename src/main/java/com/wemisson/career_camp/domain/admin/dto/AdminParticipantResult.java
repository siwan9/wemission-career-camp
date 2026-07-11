package com.wemisson.career_camp.domain.admin.dto;

import com.wemisson.career_camp.domain.participant.entity.ParticipantEntity;
import com.wemisson.career_camp.domain.participant.entity.ParticipantLectureEntity;

public record AdminParticipantResult(
	Long participantId,
	Long participantLectureId,
	String name,
	String participantTypeName,
	String churchName,
	String phoneNumber,
	String morningLectureName,
	String afternoonLectureName
) {

	public static AdminParticipantResult of(
		ParticipantEntity participantEntity,
		ParticipantLectureEntity participantLectureEntity
	) {
		return new AdminParticipantResult(
			participantEntity.getId(),
			participantLectureEntity == null ? null : participantLectureEntity.getId(),
			participantEntity.getName(),
			participantEntity.getType().getType().name().equals("STUDENT") ? "학생" : "교사",
			participantEntity.getRecruitmentChurchEntity().getName(),
			participantEntity.getPhoneNumber(),
			participantLectureEntity == null || participantLectureEntity.getMorningLectureEntity() == null
				? "미신청"
				: participantLectureEntity.getMorningLectureEntity().getSpeakerName(),
			participantLectureEntity == null || participantLectureEntity.getAfternoonLectureEntity() == null
				? "미신청"
				: participantLectureEntity.getAfternoonLectureEntity().getSpeakerName()
		);
	}
}
