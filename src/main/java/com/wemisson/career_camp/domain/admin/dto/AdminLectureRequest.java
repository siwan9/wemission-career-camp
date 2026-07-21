package com.wemisson.career_camp.domain.admin.dto;

import com.wemisson.career_camp.domain.recruitment.dto.LectureType;

public record AdminLectureRequest(
	String speakerName,
	String speakerJob,
	String description,
	LectureType type,
	Boolean isOpen,
	Integer maxCapacity
) {
	public boolean open() {
		return Boolean.TRUE.equals(isOpen);
	}
}
