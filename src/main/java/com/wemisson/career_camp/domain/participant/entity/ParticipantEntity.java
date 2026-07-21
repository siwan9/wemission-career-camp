package com.wemisson.career_camp.domain.participant.entity;

import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentChurchEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "participants")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class ParticipantEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recruitment_church_id", nullable = false)
	private RecruitmentChurchEntity recruitmentChurchEntity;

	@Pattern(regexp = "^\\d{10,11}$", message = "전화번호는 10~11자리 숫자여야 합니다.")
	@Column(nullable = false)
	private String phoneNumber;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recruitment_id", nullable = false)
	private RecruitmentEntity recruitmentEntity;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "participant_type_id", nullable = false)
	private ParticipantTypeEntity participantTypeEntity;

	public static ParticipantEntity create(
		String name,
		RecruitmentEntity recruitmentEntity,
		ParticipantTypeEntity type,
		RecruitmentChurchEntity recruitmentChurchEntity,
		String phoneNumber
	) {
		ParticipantEntity participantEntity = new ParticipantEntity();
		participantEntity.name = name;
		participantEntity.recruitmentEntity = recruitmentEntity;
		participantEntity.participantTypeEntity = type;
		participantEntity.recruitmentChurchEntity = recruitmentChurchEntity;
		participantEntity.phoneNumber = phoneNumber;

		return participantEntity;
	}

	public void update(
		String name,
		ParticipantTypeEntity type,
		RecruitmentChurchEntity recruitmentChurchEntity,
		String phoneNumber
	) {
		this.name = name;
		this.participantTypeEntity = type;
		this.recruitmentChurchEntity = recruitmentChurchEntity;
		this.phoneNumber = phoneNumber;
	}

	public ParticipantTypeEntity getType() {
		return participantTypeEntity;
	}
}
