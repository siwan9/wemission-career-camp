package com.wemisson.career_camp.domain.recruitment.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
	private LocalDate startAt;

	@Column(nullable = false)
	private LocalDate endAt;

	@Column(nullable = false)
	private boolean isOpen;
}
