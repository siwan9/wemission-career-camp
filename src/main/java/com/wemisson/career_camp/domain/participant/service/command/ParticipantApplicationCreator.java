package com.wemisson.career_camp.domain.participant.service.command;

import org.springframework.stereotype.Service;

import com.wemisson.career_camp.domain.participant.dto.ParticipantCreateRequest;
import com.wemisson.career_camp.domain.participant.entity.ParticipantEntity;
import com.wemisson.career_camp.domain.participant.entity.ParticipantLectureEntity;
import com.wemisson.career_camp.domain.participant.entity.ParticipantTypeEntity;
import com.wemisson.career_camp.domain.participant.repository.ParticipantRepository;
import com.wemisson.career_camp.domain.participant.repository.ParticipantTypeRepository;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentChurchEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentChurchRepository;
import com.wemisson.career_camp.domain.recruitment.service.query.RecruitmentQueryService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ParticipantApplicationCreator {

	private final ParticipantRepository participantRepository;
	private final ParticipantTypeRepository participantTypeRepository;
	private final RecruitmentChurchRepository recruitmentChurchRepository;
	private final RecruitmentQueryService recruitmentService;

	public ParticipantLectureEntity create(
		ParticipantCreateRequest request,
		RecruitmentEntity recruitmentEntity
	) {
		ParticipantEntity participantEntity = participantRepository.save(
			ParticipantEntity.create(
				request.name(),
				recruitmentEntity,
				findParticipantType(request.participantTypeId()),
				findRecruitmentChurch(request.churchId(), recruitmentEntity),
				request.phoneNumber()
			)
		);

		return ParticipantLectureEntity.create(participantEntity);
	}

	private ParticipantTypeEntity findParticipantType(Long participantTypeId) {
		return participantTypeRepository.findById(participantTypeId)
			.orElseThrow(() -> new IllegalArgumentException("참가자 유형을 찾을 수 없습니다."));
	}

	private RecruitmentChurchEntity findRecruitmentChurch(Long churchId, RecruitmentEntity recruitmentEntity) {
		if (!recruitmentService.isSelectableChurch(recruitmentEntity, churchId)) {
			throw new IllegalArgumentException("현재 모집에서 선택할 수 없는 교회입니다.");
		}

		return recruitmentChurchRepository.getReferenceById(churchId);
	}
}
