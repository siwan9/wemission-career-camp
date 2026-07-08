package com.wemisson.career_camp.domain.participant.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wemisson.career_camp.domain.participant.dto.ParticipantLookupResult;
import com.wemisson.career_camp.domain.participant.dto.ParticipantCreateRequest;
import com.wemisson.career_camp.domain.participant.entity.ParticipantEntity;
import com.wemisson.career_camp.domain.participant.entity.ParticipantLectureEntity;
import com.wemisson.career_camp.domain.participant.repository.ParticipantLectureRepository;
import com.wemisson.career_camp.domain.participant.repository.ParticipantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ParticipantLookupService {

	private final ParticipantRepository participantRepository;
	private final ParticipantLectureRepository participantLectureRepository;

	@Transactional(readOnly = true)
	public List<ParticipantLookupResult> lookup(String phoneNumber, String password) {
		List<ParticipantEntity> participants = participantRepository.findByPhoneNumberAndPassword(
			phoneNumber,
			password
		);

		if (participants.isEmpty()) {
			return List.of();
		}

		return participantLectureRepository.findByParticipantEntityIn(participants)
			.stream()
			.map(ParticipantLookupResult::from)
			.toList();
	}

	@Transactional(readOnly = true)
	public ParticipantLectureEntity findParticipantLecture(Long participantLectureId) {
		return participantLectureRepository.findById(participantLectureId)
			.orElseThrow(() -> new IllegalArgumentException("신청 정보를 찾을 수 없습니다."));
	}

	public ParticipantCreateRequest toCreateRequest(ParticipantLectureEntity participantLectureEntity) {
		ParticipantEntity participant = participantLectureEntity.getParticipantEntity();

		return new ParticipantCreateRequest(
			participant.getName(),
			participant.getType().getId(),
			participant.getRecruitmentChurchEntity().getId(),
			participant.getPhoneNumber(),
			participant.getPassword()
		);
	}
}
