package com.wemisson.career_camp.domain.recruitment.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wemisson.career_camp.domain.recruitment.dto.LectureType;
import com.wemisson.career_camp.domain.recruitment.entity.LectureEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;
import com.wemisson.career_camp.domain.recruitment.repository.LectureRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LectureService {

	private final LectureRepository lectureRepository;

	@Transactional(readOnly = true)
	public List<LectureEntity> findOpenLectures(RecruitmentEntity recruitmentEntity, LectureType type) {
		return lectureRepository.findOpenLectures(recruitmentEntity, type);
	}
}
