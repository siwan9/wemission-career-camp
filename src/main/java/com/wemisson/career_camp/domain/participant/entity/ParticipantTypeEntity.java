package com.wemisson.career_camp.domain.participant.entity;

import com.wemisson.career_camp.domain.participant.dto.ParticipantType;

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
@Table(name = "participant_types")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class ParticipantTypeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, unique = true)
	private ParticipantType type;

	public static ParticipantTypeEntity from(ParticipantType type) {
		ParticipantTypeEntity participantTypeEntity = new ParticipantTypeEntity();
		participantTypeEntity.type = type;

		return participantTypeEntity;
	}

	public boolean isStudent() {
		return type == ParticipantType.STUDENT;
	}

	public boolean isTeacher() {
		return type == ParticipantType.TEACHER;
	}
}
