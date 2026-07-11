package com.wemisson.career_camp.domain.recruitment.entity;

import com.wemisson.career_camp.domain.participant.entity.ParticipantTypeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "recruitment_participant_types")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class RecruitmentParticipantTypeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "can_select_morning_lecture", nullable = false)
	private Boolean canSelectMorningLecture;

	@Column(name = "can_select_afternoon_lecture", nullable = false)
	private Boolean canSelectAfternoonLecture;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recruitment_id", nullable = false)
	private RecruitmentEntity recruitmentEntity;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "participant_type_id", nullable = false)
	private ParticipantTypeEntity participantTypeEntity;

	public static RecruitmentParticipantTypeEntity create(
		RecruitmentEntity recruitmentEntity,
		ParticipantTypeEntity participantTypeEntity,
		boolean canSelectMorningLecture,
		boolean canSelectAfternoonLecture
	) {
		RecruitmentParticipantTypeEntity entity = new RecruitmentParticipantTypeEntity();
		entity.recruitmentEntity = recruitmentEntity;
		entity.participantTypeEntity = participantTypeEntity;
		entity.canSelectMorningLecture = canSelectMorningLecture;
		entity.canSelectAfternoonLecture = canSelectAfternoonLecture;

		return entity;
	}

	public void updateLecturePermission(
		boolean canSelectMorningLecture,
		boolean canSelectAfternoonLecture
	) {
		this.canSelectMorningLecture = canSelectMorningLecture;
		this.canSelectAfternoonLecture = canSelectAfternoonLecture;
	}

	public boolean canSelectMorningLecture() {
		return Boolean.TRUE.equals(canSelectMorningLecture);
	}

	public boolean canSelectAfternoonLecture() {
		return Boolean.TRUE.equals(canSelectAfternoonLecture);
	}
}
