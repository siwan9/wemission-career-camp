package com.wemisson.career_camp.domain.participant.dto;

public enum ParticipantType {
	STUDENT("학생"),
	TEACHER("교사");

	private final String description;

	ParticipantType(String description) {
		this.description = description;
	}

	public String getDescription() {
		return description;
	}
}
