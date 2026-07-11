package com.wemisson.career_camp.domain.recruitment.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "recruitments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class RecruitmentEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String description;

	@Column(nullable = false)
	private String notice;

	@Column(nullable = false)
	private LocalDateTime startAt;

	@Column(nullable = false)
	private LocalDateTime endAt;

	@Column(nullable = false)
	private boolean isOpen;

	public static RecruitmentEntity create(
		String name,
		String description,
		String notice,
		LocalDateTime startAt,
		LocalDateTime endAt,
		boolean isOpen
	) {
		RecruitmentEntity recruitmentEntity = new RecruitmentEntity();
		recruitmentEntity.name = name;
		recruitmentEntity.description = description;
		recruitmentEntity.notice = notice;
		recruitmentEntity.startAt = startAt;
		recruitmentEntity.endAt = endAt;
		recruitmentEntity.isOpen = isOpen;

		return recruitmentEntity;
	}

	public void update(
		String name,
		String description,
		String notice,
		LocalDateTime startAt,
		LocalDateTime endAt,
		boolean isOpen
	) {
		this.name = name;
		this.description = description;
		this.notice = notice;
		this.startAt = startAt;
		this.endAt = endAt;
		this.isOpen = isOpen;
	}

	public void changeOpen(boolean isOpen) {
		this.isOpen = isOpen;
	}
}
