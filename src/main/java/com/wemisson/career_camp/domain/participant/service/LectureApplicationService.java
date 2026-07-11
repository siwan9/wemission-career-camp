package com.wemisson.career_camp.domain.participant.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wemisson.career_camp.domain.participant.dto.ParticipantCreateRequest;
import com.wemisson.career_camp.domain.participant.dto.LectureApplicationResult;
import com.wemisson.career_camp.domain.participant.entity.ParticipantEntity;
import com.wemisson.career_camp.domain.participant.entity.ParticipantLectureEntity;
import com.wemisson.career_camp.domain.participant.entity.ParticipantTypeEntity;
import com.wemisson.career_camp.domain.participant.repository.ParticipantLectureRepository;
import com.wemisson.career_camp.domain.participant.repository.ParticipantRepository;
import com.wemisson.career_camp.domain.participant.repository.ParticipantTypeRepository;
import com.wemisson.career_camp.domain.recruitment.dto.LectureType;
import com.wemisson.career_camp.domain.recruitment.entity.LectureEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentChurchEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentParticipantTypeEntity;
import com.wemisson.career_camp.domain.recruitment.repository.LectureRepository;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentChurchRepository;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentParticipantTypeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LectureApplicationService {

	private final ParticipantRepository participantRepository;
	private final ParticipantLectureRepository participantLectureRepository;
	private final LectureRepository lectureRepository;
	private final ParticipantTypeRepository participantTypeRepository;
	private final RecruitmentParticipantTypeRepository recruitmentParticipantTypeRepository;
	private final RecruitmentChurchRepository recruitmentChurchRepository;

	@Transactional
	public LectureApplicationResult apply(
		ParticipantCreateRequest request,
		Long participantLectureId,
		Long lectureId
	) {
		return apply(request, participantLectureId, lectureId, false);
	}

	@Transactional
	public LectureApplicationResult apply(
		ParticipantCreateRequest request,
		Long participantLectureId,
		Long lectureId,
		boolean allowFull
	) {
		LectureEntity lectureEntity = lectureRepository.findById(lectureId)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 특강입니다."));

		validate(request, lectureEntity, allowFull);

		ParticipantLectureEntity participantLectureEntity = getOrCreateParticipantLecture(
			request,
			participantLectureId,
			lectureEntity
		);

		LectureEntity previousLectureEntity = participantLectureEntity.getAppliedLecture(lectureEntity.getType());
		if (previousLectureEntity != null && previousLectureEntity.getId().equals(lectureEntity.getId())) {
			return toResult(participantLectureEntity, lectureEntity);
		}

		if (previousLectureEntity != null) {
			previousLectureEntity.decreaseParticipantCount();
		}

		if (allowFull) {
			lectureEntity.forceIncreaseParticipantCount();
		} else {
			lectureEntity.increaseParticipantCount();
		}
		participantLectureEntity.apply(lectureEntity);

		return toResult(participantLectureRepository.save(participantLectureEntity), lectureEntity);
	}

	@Transactional
	public LectureApplicationResult cancel(Long participantLectureId, Long lectureId) {
		if (participantLectureId == null) {
			throw new IllegalArgumentException("신청 정보를 찾을 수 없습니다.");
		}

		ParticipantLectureEntity participantLectureEntity = participantLectureRepository.findById(participantLectureId)
			.orElseThrow(() -> new IllegalArgumentException("신청 정보를 찾을 수 없습니다."));
		LectureEntity lectureEntity = lectureRepository.findById(lectureId)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 특강입니다."));
		LectureEntity appliedLectureEntity = participantLectureEntity.getAppliedLecture(lectureEntity.getType());

		if (appliedLectureEntity == null || !appliedLectureEntity.getId().equals(lectureEntity.getId())) {
			throw new IllegalArgumentException("신청한 특강이 아닙니다.");
		}

		lectureEntity.decreaseParticipantCount();
		participantLectureEntity.cancel(lectureEntity);

		if (participantLectureEntity.hasNoAppliedLecture()) {
			ParticipantEntity participantEntity = participantLectureEntity.getParticipantEntity();
			participantLectureRepository.delete(participantLectureEntity);
			participantRepository.delete(participantEntity);

			return toResult(participantLectureEntity, lectureEntity, true);
		}

		return toResult(participantLectureEntity, lectureEntity);
	}

	private ParticipantLectureEntity getOrCreateParticipantLecture(
		ParticipantCreateRequest request,
		Long participantLectureId,
		LectureEntity lectureEntity
	) {
		if (participantLectureId != null) {
			return participantLectureRepository.findById(participantLectureId)
				.orElseThrow(() -> new IllegalArgumentException("신청 정보를 찾을 수 없습니다."));
		}

		ParticipantEntity participantEntity = participantRepository.save(
			ParticipantEntity.create(
				request.name(),
				lectureEntity.getRecruitmentEntity(),
				findParticipantType(request.participantTypeId()),
				findRecruitmentChurch(request.churchId(), lectureEntity),
				request.phoneNumber(),
				request.password()
			)
		);

		return ParticipantLectureEntity.create(participantEntity);
	}

	private void validate(ParticipantCreateRequest request, LectureEntity lectureEntity, boolean allowFull) {
		if (!allowFull && !lectureEntity.isOpen()) {
			throw new IllegalStateException("아직 신청 준비중인 특강입니다.");
		}

		if (!allowFull && lectureEntity.isFull()) {
			throw new IllegalStateException("신청 가능한 자리가 없습니다.");
		}

		RecruitmentParticipantTypeEntity rule = recruitmentParticipantTypeRepository
			.findByRecruitmentEntityAndParticipantTypeEntityId(
				lectureEntity.getRecruitmentEntity(),
				request.participantTypeId()
			)
			.orElseThrow(() -> new IllegalArgumentException("현재 모집에서 신청할 수 없는 참가자 유형입니다."));

		if (!rule.canSelectMorningLecture() && !rule.canSelectAfternoonLecture()) {
			throw new IllegalArgumentException("현재 모집에서 신청할 수 없는 참가자 유형입니다.");
		}

		if (!canSelect(rule, lectureEntity.getType())) {
			throw new IllegalArgumentException("선택한 참가자 유형으로 신청할 수 없는 특강 시간대입니다.");
		}
	}

	private ParticipantTypeEntity findParticipantType(Long participantTypeId) {
		return participantTypeRepository.findById(participantTypeId)
			.orElseThrow(() -> new IllegalArgumentException("참가자 유형을 찾을 수 없습니다."));
	}

	private RecruitmentChurchEntity findRecruitmentChurch(Long churchId, LectureEntity lectureEntity) {
		return recruitmentChurchRepository.findByIdAndRecruitmentEntity(
				churchId,
				lectureEntity.getRecruitmentEntity()
			)
			.orElseThrow(() -> new IllegalArgumentException("현재 모집에서 선택할 수 없는 교회입니다."));
	}

	private boolean canSelect(RecruitmentParticipantTypeEntity rule, LectureType lectureType) {
		if (lectureType == LectureType.AM) {
			return rule.canSelectMorningLecture();
		}

		return rule.canSelectAfternoonLecture();
	}

	private LectureApplicationResult toResult(
		ParticipantLectureEntity participantLectureEntity,
		LectureEntity lectureEntity
	) {
		return new LectureApplicationResult(
			participantLectureEntity.getId(),
			lectureEntity.getId(),
			lectureEntity.getType(),
			lectureEntity.getRemainingCapacity(),
			false
		);
	}

	private LectureApplicationResult toResult(
		ParticipantLectureEntity participantLectureEntity,
		LectureEntity lectureEntity,
		boolean participantDeleted
	) {
		return new LectureApplicationResult(
			participantLectureEntity.getId(),
			lectureEntity.getId(),
			lectureEntity.getType(),
			lectureEntity.getRemainingCapacity(),
			participantDeleted
		);
	}
}
