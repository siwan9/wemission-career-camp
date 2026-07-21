package com.wemisson.career_camp.domain.recruitment.entity;

import org.hibernate.annotations.DynamicUpdate;

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
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class LectureEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "speaker_name", nullable = false)
	private String speakerName;

	@Column(name = "speaker_job", nullable = false)
	private String speakerJob;

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

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recruitment_id", nullable = false)
	private RecruitmentEntity recruitmentEntity;

	public static LectureEntity create(
		RecruitmentEntity recruitmentEntity,
		String speakerName,
		String speakerJob,
		String description,
		LectureType type,
		boolean isOpen,
		Integer maxCapacity,
		Integer sortOrder
	) {
		LectureEntity lectureEntity = new LectureEntity();
		lectureEntity.recruitmentEntity = recruitmentEntity;
		lectureEntity.speakerName = speakerName;
		lectureEntity.speakerJob = speakerJob;
		lectureEntity.description = description;
		lectureEntity.type = type;
		lectureEntity.isOpen = isOpen;
		lectureEntity.maxCapacity = maxCapacity;
		lectureEntity.participantCount = 0;
		lectureEntity.sortOrder = sortOrder;

		return lectureEntity;
	}

	public int getRemainingCapacity() {
		return maxCapacity - participantCount;
	}

	public boolean isFull() {
		return getRemainingCapacity() <= 0;
	}

	public void update(
		String speakerName,
		String speakerJob,
		String description,
		LectureType type,
		boolean isOpen,
		Integer maxCapacity
	) {
		this.speakerName = speakerName;
		this.speakerJob = speakerJob;
		this.description = description;
		this.type = type;
		this.isOpen = isOpen;
		this.maxCapacity = maxCapacity;
	}

	public void increaseParticipantCount() {
		if (isFull()) {
			throw new IllegalStateException("신청 가능한 자리가 없습니다.");
		}

		this.participantCount++;
	}

	public void forceIncreaseParticipantCount() {
		this.participantCount++;
	}

	public void decreaseParticipantCount() {
		if (participantCount <= 0) {
			return;
		}

		this.participantCount--;
	}

	public void changeSortOrder(Integer sortOrder) {
		this.sortOrder = sortOrder;
	}
}
