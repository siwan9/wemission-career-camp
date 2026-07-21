package com.wemisson.career_camp.domain.participant.entity;

import java.time.LocalDateTime;

import com.wemisson.career_camp.domain.recruitment.dto.LectureType;
import com.wemisson.career_camp.domain.recruitment.entity.LectureEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
	name = "participant_lecture_drafts",
	indexes = {
		@Index(name = "idx_participant_lecture_drafts_lecture_expires", columnList = "lecture_id, expires_at"),
		@Index(name = "idx_participant_lecture_drafts_token_expires", columnList = "draft_token, expires_at"),
		@Index(name = "idx_participant_lecture_drafts_expires", columnList = "expires_at")
	},
	uniqueConstraints = @UniqueConstraint(
		name = "uk_participant_lecture_drafts_token_type",
		columnNames = {"draft_token", "lecture_type"}
	)
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class ParticipantLectureDraftEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "draft_token", nullable = false)
	private String draftToken;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "participant_id", nullable = true)
	private ParticipantEntity participantEntity;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recruitment_id", nullable = false)
	private RecruitmentEntity recruitmentEntity;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "lecture_id", nullable = false)
	private LectureEntity lectureEntity;

	@Enumerated(EnumType.STRING)
	@Column(name = "lecture_type", nullable = false)
	private LectureType lectureType;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public static ParticipantLectureDraftEntity create(
		String draftToken,
		ParticipantEntity participantEntity,
		RecruitmentEntity recruitmentEntity,
		LectureEntity lectureEntity,
		LocalDateTime expiresAt,
		LocalDateTime now
	) {
		ParticipantLectureDraftEntity draftEntity = new ParticipantLectureDraftEntity();
		draftEntity.draftToken = draftToken;
		draftEntity.participantEntity = participantEntity;
		draftEntity.recruitmentEntity = recruitmentEntity;
		draftEntity.lectureEntity = lectureEntity;
		draftEntity.lectureType = lectureEntity.getType();
		draftEntity.expiresAt = expiresAt;
		draftEntity.createdAt = now;
		draftEntity.updatedAt = now;

		return draftEntity;
	}

	public void updateLecture(
		LectureEntity lectureEntity,
		LocalDateTime expiresAt,
		LocalDateTime now
	) {
		this.lectureEntity = lectureEntity;
		this.lectureType = lectureEntity.getType();
		this.expiresAt = expiresAt;
		this.updatedAt = now;
	}
}
