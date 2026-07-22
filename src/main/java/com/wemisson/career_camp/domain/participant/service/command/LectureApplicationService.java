package com.wemisson.career_camp.domain.participant.service.command;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wemisson.career_camp.domain.participant.dto.LectureApplicationResult;
import com.wemisson.career_camp.domain.participant.dto.ParticipantCreateRequest;
import com.wemisson.career_camp.domain.participant.dto.ParticipantLectureDraftResult;
import com.wemisson.career_camp.domain.participant.dto.ParticipantLectureDraftStatus;
import com.wemisson.career_camp.domain.participant.entity.ParticipantEntity;
import com.wemisson.career_camp.domain.participant.entity.ParticipantLectureDraftEntity;
import com.wemisson.career_camp.domain.participant.service.draft.LectureCapacityService;
import com.wemisson.career_camp.domain.participant.service.draft.LectureDraftService;
import com.wemisson.career_camp.domain.recruitment.dto.LectureType;
import com.wemisson.career_camp.domain.recruitment.entity.LectureEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;
import com.wemisson.career_camp.domain.recruitment.repository.LectureRepository;
import com.wemisson.career_camp.domain.recruitment.service.query.RecruitmentQueryService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LectureApplicationService {

	private final LectureRepository lectureRepository;
	private final RecruitmentQueryService recruitmentService;
	private final LectureCapacityService lectureCapacityService;
	private final LectureDraftService lectureDraftService;
	private final LectureApplicationFinalizer lectureApplicationFinalizer;
	private final Clock clock;

	@Transactional
	public ParticipantLectureDraftResult holdDraft(
		ParticipantCreateRequest request,
		Long participantLectureId,
		Long expectedRecruitmentId,
		Long lectureId,
		String draftToken,
		boolean allowFull
	) {
		return holdDraft(
			request,
			participantLectureId,
			expectedRecruitmentId,
			lectureId,
			draftToken,
			allowFull,
			true
		);
	}

	@Transactional
	public ParticipantLectureDraftResult holdDraft(
		ParticipantCreateRequest request,
		Long participantLectureId,
		Long expectedRecruitmentId,
		Long lectureId,
		String draftToken,
		boolean allowFull,
		boolean allowCompletedApplicationModification
	) {
		if (participantLectureId != null && !allowCompletedApplicationModification) {
			throw new IllegalStateException("신청 완료 후에는 수정 화면에서만 특강을 변경할 수 있습니다.");
		}

		ParticipantEntity participantEntity = lectureDraftService.lockParticipantApplication(participantLectureId);
		List<ParticipantLectureDraftEntity> lockedDrafts = lectureDraftService.lockDrafts(draftToken);
		LectureEntity lectureEntity = lectureRepository.findByIdForUpdate(lectureId)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 특강입니다."));
		validateRecruitmentScope(expectedRecruitmentId, participantEntity, lectureEntity);
		validateSelectable(request, lectureEntity, allowFull);
		LectureDraftService.DraftSelection selection = lectureDraftService.hold(
			participantEntity,
			lectureEntity,
			draftToken,
			allowFull,
			lockedDrafts
		);

		return new ParticipantLectureDraftResult(
			selection.lectureId(),
			selection.lectureType(),
			selection.remainingCapacity(),
			selection.held(),
			isReadyToFinalize(request, participantLectureId, expectedRecruitmentId, draftToken)
		);
	}

	@Transactional
	public ParticipantLectureDraftResult releaseDraft(
		ParticipantCreateRequest request,
		Long participantLectureId,
		Long expectedRecruitmentId,
		Long lectureId,
		String draftToken
	) {
		ParticipantEntity participantEntity = lectureDraftService.lockParticipantApplication(participantLectureId);
		List<ParticipantLectureDraftEntity> lockedDrafts = lectureDraftService.lockDrafts(draftToken);
		LectureEntity lectureEntity = lectureRepository.findByIdForUpdate(lectureId)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 특강입니다."));
		validateRecruitmentScope(expectedRecruitmentId, participantEntity, lectureEntity);
		LectureDraftService.DraftSelection selection = lectureDraftService.release(lectureEntity, lockedDrafts);

		return new ParticipantLectureDraftResult(
			selection.lectureId(),
			selection.lectureType(),
			selection.remainingCapacity(),
			selection.held(),
			isReadyToFinalize(request, participantLectureId, expectedRecruitmentId, draftToken)
		);
	}

	@Transactional(readOnly = true)
	public List<ParticipantLectureDraftEntity> findActiveDrafts(String draftToken) {
		return lectureDraftService.findActiveDrafts(draftToken);
	}

	public void releaseDrafts(String draftToken) {
		if (draftToken == null || draftToken.isBlank()) {
			return;
		}

		lectureDraftService.releaseAll(draftToken);
	}

	@Transactional(readOnly = true)
	public ParticipantLectureDraftStatus getDraftStatus(
		ParticipantCreateRequest request,
		Long participantLectureId,
		Long expectedRecruitmentId,
		String draftToken
	) {
		Map<LectureType, ParticipantLectureDraftStatus.SelectedLecture> selectedLectures =
			new java.util.EnumMap<>(LectureType.class);
		LocalDateTime now = LocalDateTime.now(clock);
		List<ParticipantLectureDraftEntity> activeDrafts = findActiveDrafts(draftToken);
		validateDraftRecruitmentScope(expectedRecruitmentId, activeDrafts);

		activeDrafts.forEach(draft -> selectedLectures.put(
			draft.getLectureType(),
			new ParticipantLectureDraftStatus.SelectedLecture(
				draft.getLectureEntity().getId(),
				lectureCapacityService.getRemainingCapacity(draft.getLectureEntity(), now),
				draft.getExpiresAt()
			)
		));

		return new ParticipantLectureDraftStatus(
			selectedLectures,
			isReadyToFinalize(request, participantLectureId, expectedRecruitmentId, draftToken),
			activeDrafts.stream()
				.map(ParticipantLectureDraftEntity::getExpiresAt)
				.min(LocalDateTime::compareTo)
				.orElse(null)
		);
	}

	@Transactional(readOnly = true)
	public boolean isReadyToFinalize(
		ParticipantCreateRequest request,
		Long participantLectureId,
		Long expectedRecruitmentId,
		String draftToken
	) {
		return lectureApplicationFinalizer.isReadyToFinalize(
			request,
			participantLectureId,
			expectedRecruitmentId,
			draftToken
		);
	}

	@Transactional
	public LectureApplicationResult finalizeDraft(
		ParticipantCreateRequest request,
		Long participantLectureId,
		Long expectedRecruitmentId,
		String draftToken,
		boolean allowFull
	) {
		return lectureApplicationFinalizer.finalizeDraft(
			request,
			participantLectureId,
			expectedRecruitmentId,
			draftToken,
			allowFull
		);
	}

	private void validateSelectable(
		ParticipantCreateRequest request,
		LectureEntity lectureEntity,
		boolean allowFull
	) {
		RecruitmentEntity recruitmentEntity = lectureEntity.getRecruitmentEntity();

		if (!allowFull && !recruitmentEntity.canAcceptApplications()) {
			throw new IllegalStateException("현재 수강신청을 진행할 수 있는 모집이 아닙니다.");
		}
		if (!allowFull && !lectureEntity.isOpen()) {
			throw new IllegalStateException("아직 신청 준비중인 특강입니다.");
		}

		RecruitmentQueryService.ParticipantTypeRule rule = recruitmentService.findParticipantTypeRule(
			lectureEntity.getRecruitmentEntity(),
			request.participantTypeId()
		);

		if (!rule.canSelectMorningLecture() && !rule.canSelectAfternoonLecture()) {
			throw new IllegalArgumentException("현재 모집에서 신청할 수 없는 참가자 유형입니다.");
		}

		if (!canSelect(rule, lectureEntity.getType())) {
			throw new IllegalArgumentException("선택한 참가자 유형으로 신청할 수 없는 특강 시간대입니다.");
		}
	}

	private boolean canSelect(RecruitmentQueryService.ParticipantTypeRule rule, LectureType lectureType) {
		if (lectureType == LectureType.AM) {
			return rule.canSelectMorningLecture();
		}

		return rule.canSelectAfternoonLecture();
	}

	private void validateRecruitmentScope(
		Long expectedRecruitmentId,
		ParticipantEntity participantEntity,
		LectureEntity lectureEntity
	) {
		if (expectedRecruitmentId == null
			|| !lectureEntity.getRecruitmentEntity().getId().equals(expectedRecruitmentId)) {
			throw new IllegalArgumentException("현재 신청 중인 모집의 특강이 아닙니다.");
		}
		if (participantEntity != null
			&& !participantEntity.getRecruitmentEntity().getId().equals(expectedRecruitmentId)) {
			throw new IllegalArgumentException("수정 중인 신청과 모집 정보가 일치하지 않습니다.");
		}
	}

	private void validateDraftRecruitmentScope(
		Long expectedRecruitmentId,
		List<ParticipantLectureDraftEntity> drafts
	) {
		if (expectedRecruitmentId == null || drafts.stream().anyMatch(
			draft -> !draft.getRecruitmentEntity().getId().equals(expectedRecruitmentId)
		)) {
			throw new IllegalArgumentException("현재 신청 중인 모집과 임시점유 정보가 일치하지 않습니다.");
		}
	}

}
