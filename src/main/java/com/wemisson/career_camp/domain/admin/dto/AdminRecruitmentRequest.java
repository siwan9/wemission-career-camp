package com.wemisson.career_camp.domain.admin.dto;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import com.wemisson.career_camp.domain.recruitment.dto.RecruitmentStatus;

public record AdminRecruitmentRequest(
	String name,
	String description,
	String notice,
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
	LocalDateTime startAt,
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
	LocalDateTime endAt,
	RecruitmentStatus status
) {
}
