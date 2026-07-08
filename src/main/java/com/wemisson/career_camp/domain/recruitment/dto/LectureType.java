package com.wemisson.career_camp.domain.recruitment.dto;

public enum LectureType {
	AM("오전"),
	PM("오후");

	private final String description;
	LectureType(String description) {
		this.description = description;
	}
}
