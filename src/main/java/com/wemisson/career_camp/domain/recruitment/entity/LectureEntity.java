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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "lectures")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class LectureEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private LectureType type;

	@Column(nullable = false)
	private boolean isOpen;

	@Column(nullable = false)
	private Integer maxCapacity;

	@Column(nullable = false)
	private Integer participantCount;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recruitment_id", nullable = false)
	private RecruitmentEntity recruitmentEntity;

	public int getRemainingCapacity() {
		return maxCapacity - participantCount;
	}

	public boolean isFull() {
		return getRemainingCapacity() <= 0;
	}

	public void increaseParticipantCount() {
		if (isFull()) {
			throw new IllegalStateException("신청 가능한 자리가 없습니다.");
		}

		this.participantCount++;
	}

	public void decreaseParticipantCount() {
		if (participantCount <= 0) {
			return;
		}

		this.participantCount--;
	}
}
