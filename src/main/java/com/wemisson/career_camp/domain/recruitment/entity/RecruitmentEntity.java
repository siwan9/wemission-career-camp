package com.wemisson.career_camp.domain.recruitment.entity;

import java.time.LocalDateTime;

import com.wemisson.career_camp.domain.recruitment.dto.RecruitmentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RecruitmentStatus status;

	@Column(name = "status_changed_at", nullable = false)
	private LocalDateTime statusChangedAt;

	public static RecruitmentEntity create(
		String name,
		String description,
		String notice,
		LocalDateTime startAt,
		LocalDateTime endAt,
		RecruitmentStatus status,
		LocalDateTime statusChangedAt
	) {
		RecruitmentEntity recruitmentEntity = new RecruitmentEntity();
		recruitmentEntity.name = name;
		recruitmentEntity.description = description;
		recruitmentEntity.notice = notice;
		recruitmentEntity.startAt = startAt;
		recruitmentEntity.endAt = endAt;
		recruitmentEntity.status = status;
		recruitmentEntity.statusChangedAt = statusChangedAt;

		return recruitmentEntity;
	}

	public void update(
		String name,
		String description,
		String notice,
		LocalDateTime startAt,
		LocalDateTime endAt,
		RecruitmentStatus status,
		LocalDateTime changedAt
	) {
		this.name = name;
		this.description = description;
		this.notice = notice;
		this.startAt = startAt;
		this.endAt = endAt;
		if (this.status != status) {
			changeStatus(status, changedAt);
		}
	}

	public void changeStatus(RecruitmentStatus status, LocalDateTime changedAt) {
		this.status = status;
		this.statusChangedAt = changedAt;
	}

	public boolean hasUnprocessedScheduleBoundary(LocalDateTime scheduledAt) {
		return statusChangedAt.isBefore(scheduledAt);
	}

	public boolean isOpen() {
		return status == RecruitmentStatus.OPEN;
	}

	public boolean isWaiting() {
		return status == RecruitmentStatus.WAITING;
	}

	public boolean isClosed() {
		return status == RecruitmentStatus.CLOSED;
	}

	public String getStatusDescription() {
		return status.getDescription();
	}
}
