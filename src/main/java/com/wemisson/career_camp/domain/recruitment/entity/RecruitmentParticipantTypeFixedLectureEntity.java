package com.wemisson.career_camp.domain.recruitment.entity;

import com.wemisson.career_camp.domain.recruitment.dto.LectureType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
	name = "recruitment_participant_type_fixed_lectures",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uk_rpt_fixed_lectures_rule_type",
			columnNames = {"recruitment_participant_type_id", "lecture_type"}
		),
		@UniqueConstraint(
			name = "uk_rpt_fixed_lectures_rule_lecture",
			columnNames = {"recruitment_participant_type_id", "lecture_id"}
		)
	}
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class RecruitmentParticipantTypeFixedLectureEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recruitment_participant_type_id", nullable = false)
	private RecruitmentParticipantTypeEntity recruitmentParticipantTypeEntity;

	@Enumerated(EnumType.STRING)
	@Column(name = "lecture_type", nullable = false)
	private LectureType lectureType;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "lecture_id", nullable = false)
	private LectureEntity lectureEntity;

	public static RecruitmentParticipantTypeFixedLectureEntity create(
		RecruitmentParticipantTypeEntity recruitmentParticipantTypeEntity,
		LectureEntity lectureEntity
	) {
		RecruitmentParticipantTypeFixedLectureEntity fixedLecture =
			new RecruitmentParticipantTypeFixedLectureEntity();
		fixedLecture.recruitmentParticipantTypeEntity = recruitmentParticipantTypeEntity;
		fixedLecture.validateRecruitment(lectureEntity);
		fixedLecture.lectureType = lectureEntity.getType();
		fixedLecture.lectureEntity = lectureEntity;

		return fixedLecture;
	}

	public void changeLecture(LectureEntity lectureEntity) {
		validateRecruitment(lectureEntity);
		if (lectureEntity.getType() != lectureType) {
			throw new IllegalArgumentException("고정 강좌의 시간대가 일치하지 않습니다.");
		}

		this.lectureEntity = lectureEntity;
	}

	private void validateRecruitment(LectureEntity lectureEntity) {
		Long ruleRecruitmentId = recruitmentParticipantTypeEntity.getRecruitmentEntity().getId();
		Long lectureRecruitmentId = lectureEntity.getRecruitmentEntity().getId();

		if (!ruleRecruitmentId.equals(lectureRecruitmentId)) {
			throw new IllegalArgumentException("고정 강좌는 참가자 타입 설정과 같은 모집에 속해야 합니다.");
		}
	}
}
