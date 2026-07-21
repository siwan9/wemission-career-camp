package com.wemisson.career_camp.domain.participant.entity;

import java.time.LocalDateTime;

import com.wemisson.career_camp.domain.recruitment.dto.LectureType;
import com.wemisson.career_camp.domain.recruitment.entity.LectureEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "participant_lectures")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class ParticipantLectureEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne
	@JoinColumn(name = "participant_id", nullable = false)
	private ParticipantEntity participantEntity;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "morning_lecture_id", nullable = true)
	private LectureEntity morningLectureEntity;

	@Column(nullable = true)
	private LocalDateTime morningLectureApplyAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "afternoon_lecture_id", nullable = true)
	private LectureEntity afternoonLectureEntity;

	@Column(nullable = true)
	private LocalDateTime afternoonLectureApplyAt;

	public static ParticipantLectureEntity create(ParticipantEntity participantEntity) {
		ParticipantLectureEntity participantLectureEntity = new ParticipantLectureEntity();
		participantLectureEntity.participantEntity = participantEntity;

		return participantLectureEntity;
	}

	public void apply(LectureEntity lectureEntity, LocalDateTime appliedAt) {
		if (lectureEntity.getType() == LectureType.AM) {
			this.morningLectureEntity = lectureEntity;
			this.morningLectureApplyAt = appliedAt;
			return;
		}

		this.afternoonLectureEntity = lectureEntity;
		this.afternoonLectureApplyAt = appliedAt;
	}

	public LectureEntity getAppliedLecture(LectureType lectureType) {
		if (lectureType == LectureType.AM) {
			return morningLectureEntity;
		}

		return afternoonLectureEntity;
	}

	public void cancel(LectureEntity lectureEntity) {
		if (lectureEntity.getType() == LectureType.AM) {
			this.morningLectureEntity = null;
			this.morningLectureApplyAt = null;
			return;
		}

		this.afternoonLectureEntity = null;
		this.afternoonLectureApplyAt = null;
	}

	public boolean hasNoAppliedLecture() {
		return morningLectureEntity == null
			&& afternoonLectureEntity == null;
	}
}
