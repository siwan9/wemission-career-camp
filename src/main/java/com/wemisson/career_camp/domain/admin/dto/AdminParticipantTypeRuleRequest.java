package com.wemisson.career_camp.domain.admin.dto;

public record AdminParticipantTypeRuleRequest(
	Boolean canSelectMorningLecture,
	Boolean canSelectAfternoonLecture
) {
	public boolean morningLectureSelectable() {
		return Boolean.TRUE.equals(canSelectMorningLecture);
	}

	public boolean afternoonLectureSelectable() {
		return Boolean.TRUE.equals(canSelectAfternoonLecture);
	}
}
