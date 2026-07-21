package com.wemisson.career_camp.domain.recruitment.dto;

public enum RecruitmentStatus {
	CLOSED("종료"),
	WAITING("대기중"),
	OPEN("모집시작");

	private final String description;

	RecruitmentStatus(String description) {
		this.description = description;
	}

	public String getDescription() {
		return description;
	}
}
