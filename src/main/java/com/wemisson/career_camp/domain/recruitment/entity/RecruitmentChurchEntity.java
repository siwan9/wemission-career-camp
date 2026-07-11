package com.wemisson.career_camp.domain.recruitment.entity;

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
@Table(name = "recruitment_churches")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class RecruitmentChurchEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recruitment_id", nullable = false)
	private RecruitmentEntity recruitmentEntity;

	public static RecruitmentChurchEntity create(
		RecruitmentEntity recruitmentEntity,
		String name,
		Integer sortOrder
	) {
		RecruitmentChurchEntity recruitmentChurchEntity = new RecruitmentChurchEntity();
		recruitmentChurchEntity.recruitmentEntity = recruitmentEntity;
		recruitmentChurchEntity.name = name;
		recruitmentChurchEntity.sortOrder = sortOrder;

		return recruitmentChurchEntity;
	}

	public void update(String name, Integer sortOrder) {
		this.name = name;
		this.sortOrder = sortOrder;
	}

	public void changeSortOrder(Integer sortOrder) {
		this.sortOrder = sortOrder;
	}
}
