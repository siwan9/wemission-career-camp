package com.wemisson.career_camp.domain.participant.service.query;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wemisson.career_camp.domain.participant.dto.ParticipantCreateRequest;
import com.wemisson.career_camp.domain.participant.entity.ParticipantEntity;
import com.wemisson.career_camp.domain.participant.entity.ParticipantLectureEntity;
import com.wemisson.career_camp.domain.participant.entity.ParticipantTypeEntity;
import com.wemisson.career_camp.domain.participant.repository.ParticipantLectureRepository;
import com.wemisson.career_camp.domain.participant.repository.ParticipantRepository;
import com.wemisson.career_camp.domain.participant.repository.ParticipantTypeRepository;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentChurchEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentChurchRepository;
import com.wemisson.career_camp.domain.recruitment.service.query.RecruitmentQueryService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ParticipantRegistrationViewService {

	private final ParticipantRepository participantRepository;
	private final ParticipantLectureRepository participantLectureRepository;
	private final ParticipantTypeRepository participantTypeRepository;
	private final RecruitmentChurchRepository recruitmentChurchRepository;
	private final RecruitmentQueryService recruitmentService;

	@Transactional(readOnly = true)
	public ParticipantEditView findParticipantEditView(Long participantId) {
		ParticipantEntity participantEntity = participantRepository.findByIdWithEditRelations(participantId)
			.orElseThrow(() -> new IllegalArgumentException("참가자 정보를 찾을 수 없습니다."));

		return new ParticipantEditView(
			toParticipantCreateRequest(participantEntity),
			participantEntity.getRecruitmentEntity()
		);
	}

	@Transactional
	public void updateParticipant(
		Long participantId,
		ParticipantCreateRequest request
	) {
		ParticipantEntity participantEntity = findParticipant(participantId);

		participantEntity.update(
			request.name(),
			findParticipantType(request.participantTypeId()),
			findRecruitmentChurch(request.churchId(), participantEntity.getRecruitmentEntity()),
			request.phoneNumber()
		);
		participantRepository.save(participantEntity);
	}

	public SelectedLectureIds findSelectedLectureIds(Long participantLectureId) {
		if (participantLectureId == null) {
			return SelectedLectureIds.empty();
		}

		return participantLectureRepository.findSelectedLectureIdsById(participantLectureId)
			.map(selectedLectureIds -> new SelectedLectureIds(
				selectedLectureIds.getMorningLectureId(),
				selectedLectureIds.getAfternoonLectureId()
			))
			.orElseGet(SelectedLectureIds::empty);
	}

	@Transactional(readOnly = true)
	public RecruitmentEntity findRecruitmentForParticipantLecture(Long participantLectureId) {
		ParticipantLectureEntity participantLectureEntity = participantLectureRepository.findByIdWithRecruitment(
				participantLectureId
			)
			.orElseThrow(() -> new IllegalArgumentException("신청 정보를 찾을 수 없습니다."));

		return participantLectureEntity.getParticipantEntity().getRecruitmentEntity();
	}

	public List<?> findSelectableParticipantTypes(RecruitmentEntity recruitmentEntity) {
		Objects.requireNonNull(recruitmentEntity, "recruitmentEntity must not be null");

		List<RecruitmentQueryService.ParticipantTypeRule> participantTypes = recruitmentService
			.findParticipantTypeRules(recruitmentEntity)
			.stream()
			.filter(rule -> rule.canSelectMorningLecture() || rule.canSelectAfternoonLecture())
			.toList();

		return participantTypes;
	}

	public List<?> findSelectableChurches(RecruitmentEntity recruitmentEntity) {
		Objects.requireNonNull(recruitmentEntity, "recruitmentEntity must not be null");

		return recruitmentService.findChurches(recruitmentEntity);
	}

	private ParticipantEntity findParticipant(Long participantId) {
		return participantRepository.findById(participantId)
			.orElseThrow(() -> new IllegalArgumentException("참가자 정보를 찾을 수 없습니다."));
	}

	private ParticipantTypeEntity findParticipantType(Long participantTypeId) {
		return participantTypeRepository.findById(participantTypeId)
			.orElseThrow(() -> new IllegalArgumentException("참가자 유형을 찾을 수 없습니다."));
	}

	private RecruitmentChurchEntity findRecruitmentChurch(
		Long churchId,
		RecruitmentEntity recruitmentEntity
	) {
		if (!recruitmentService.isSelectableChurch(recruitmentEntity, churchId)) {
			throw new IllegalArgumentException("현재 모집에서 선택할 수 없는 교회입니다.");
		}

		return recruitmentChurchRepository.getReferenceById(churchId);
	}

	private ParticipantCreateRequest toParticipantCreateRequest(ParticipantEntity participantEntity) {
		return new ParticipantCreateRequest(
			participantEntity.getName(),
			participantEntity.getType().getId(),
			participantEntity.getRecruitmentChurchEntity().getId(),
			participantEntity.getPhoneNumber()
		);
	}

	public record SelectedLectureIds(
		Long morningLectureId,
		Long afternoonLectureId
	) {
		private static SelectedLectureIds empty() {
			return new SelectedLectureIds(null, null);
		}
	}

	public record ParticipantEditView(
		ParticipantCreateRequest request,
		RecruitmentEntity recruitment
	) {
	}
}
