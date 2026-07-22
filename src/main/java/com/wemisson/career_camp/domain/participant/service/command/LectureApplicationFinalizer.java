package com.wemisson.career_camp.domain.participant.service.command;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wemisson.career_camp.domain.participant.dto.LectureApplicationResult;
import com.wemisson.career_camp.domain.participant.dto.ParticipantCreateRequest;
import com.wemisson.career_camp.domain.participant.entity.ParticipantLectureDraftEntity;
import com.wemisson.career_camp.domain.participant.entity.ParticipantLectureEntity;
import com.wemisson.career_camp.domain.participant.repository.ParticipantLectureDraftRepository;
import com.wemisson.career_camp.domain.participant.repository.ParticipantLectureRepository;
import com.wemisson.career_camp.domain.recruitment.dto.LectureType;
import com.wemisson.career_camp.domain.recruitment.entity.LectureEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;
import com.wemisson.career_camp.domain.recruitment.repository.LectureRepository;
import com.wemisson.career_camp.domain.recruitment.service.query.RecruitmentQueryService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LectureApplicationFinalizer {

	private final ParticipantLectureDraftRepository participantLectureDraftRepository;
	private final ParticipantLectureRepository participantLectureRepository;
	private final LectureRepository lectureRepository;
	private final RecruitmentQueryService recruitmentService;
	private final ParticipantApplicationCreator participantApplicationCreator;
	private final Clock clock;

	@Transactional(readOnly = true)
	public boolean isReadyToFinalize(
		ParticipantCreateRequest request,
		Long participantLectureId,
		Long expectedRecruitmentId,
		String draftToken
	) {
		try {
			ParticipantLectureEntity participantLectureEntity = findParticipantLecture(participantLectureId);
			List<ParticipantLectureDraftEntity> activeDrafts = findActiveDrafts(draftToken);
			Map<LectureType, LectureEntity> effectiveLectures = buildEffectiveLectures(
				participantLectureEntity,
				activeDrafts
			);
			RecruitmentEntity recruitmentEntity = resolveRecruitment(
				participantLectureEntity,
				effectiveLectures
			);
			validateRecruitmentScope(expectedRecruitmentId, recruitmentEntity, activeDrafts, effectiveLectures);
			RecruitmentQueryService.ParticipantTypeRule rule = recruitmentService.findParticipantTypeRule(
				recruitmentEntity,
				request.participantTypeId()
			);

			return hasRequiredLectures(rule, effectiveLectures);
		} catch (RuntimeException e) {
			return false;
		}
	}

	@Transactional
	public LectureApplicationResult finalizeDraft(
		ParticipantCreateRequest request,
		Long participantLectureId,
		Long expectedRecruitmentId,
		String draftToken,
		boolean allowFull
	) {
		FinalizeContext context = prepareFinalizeContext(
			participantLectureId,
			expectedRecruitmentId,
			draftToken
		);
		RecruitmentQueryService.ParticipantTypeRule rule = recruitmentService.findParticipantTypeRule(
			context.recruitmentEntity(),
			request.participantTypeId()
		);

		if (!hasRequiredLectures(rule, context.effectiveLectures())) {
			throw new IllegalStateException("필수 특강을 모두 선택한 뒤 최종 신청해주세요.");
		}
		if (!allowFull) {
			validateRegularFinalization(context);
		}

		ParticipantLectureEntity participantLectureEntity = context.participantLectureEntity() == null
			? participantApplicationCreator.create(request, context.recruitmentEntity())
			: context.participantLectureEntity();

		applyFinalLecture(
			participantLectureEntity,
			LectureType.AM,
			context.effectiveLectures().get(LectureType.AM),
			context.now()
		);
		applyFinalLecture(
			participantLectureEntity,
			LectureType.PM,
			context.effectiveLectures().get(LectureType.PM),
			context.now()
		);
		participantLectureRepository.save(participantLectureEntity);
		participantLectureDraftRepository.deleteAll(context.lockedDrafts());

		LectureEntity lastLecture = context.effectiveLectures().getOrDefault(
			LectureType.PM,
			context.effectiveLectures().get(LectureType.AM)
		);

		return toResult(participantLectureEntity, lastLecture);
	}

	private FinalizeContext prepareFinalizeContext(
		Long participantLectureId,
		Long expectedRecruitmentId,
		String draftToken
	) {
		if (expectedRecruitmentId == null) {
			throw new IllegalArgumentException("신청 중인 모집 정보를 찾을 수 없습니다.");
		}
		if (draftToken == null || draftToken.isBlank()) {
			throw new IllegalArgumentException("임시점유 정보를 찾을 수 없습니다. 다시 신청해주세요.");
		}

		ParticipantLectureEntity participantLectureEntity = participantLectureId == null
			? null
			: participantLectureRepository.findByIdForUpdate(participantLectureId)
				.orElseThrow(() -> new IllegalArgumentException("신청 정보를 찾을 수 없습니다."));
		List<ParticipantLectureDraftEntity> lockedDrafts = participantLectureDraftRepository
			.findByDraftTokenForUpdate(draftToken);
		TreeSet<Long> lectureIdsToLock = collectLectureIds(participantLectureEntity, lockedDrafts);
		Map<Long, LectureEntity> lockedLectures = lockLecturesById(lectureIdsToLock);
		LocalDateTime now = LocalDateTime.now(clock);
		List<ParticipantLectureDraftEntity> activeDrafts = lockedDrafts.stream()
			.filter(draft -> draft.getExpiresAt().isAfter(now))
			.toList();

		if (activeDrafts.size() != lockedDrafts.size()) {
			throw new IllegalStateException("임시점유 시간이 만료되었습니다. 현재 상태를 확인한 뒤 다시 선택해주세요.");
		}

		Map<LectureType, LectureEntity> effectiveLectures = buildEffectiveLectures(
			participantLectureEntity,
			activeDrafts,
			lockedLectures
		);
		RecruitmentEntity recruitmentEntity = resolveRecruitment(participantLectureEntity, effectiveLectures);

		validateRecruitmentScope(expectedRecruitmentId, recruitmentEntity, lockedDrafts, effectiveLectures);

		return new FinalizeContext(
			participantLectureEntity,
			lockedDrafts,
			activeDrafts,
			effectiveLectures,
			recruitmentEntity,
			now
		);
	}

	private ParticipantLectureEntity findParticipantLecture(Long participantLectureId) {
		if (participantLectureId == null) {
			return null;
		}

		return participantLectureRepository.findById(participantLectureId)
			.orElseThrow(() -> new IllegalArgumentException("신청 정보를 찾을 수 없습니다."));
	}

	private List<ParticipantLectureDraftEntity> findActiveDrafts(String draftToken) {
		if (draftToken == null || draftToken.isBlank()) {
			return List.of();
		}

		return participantLectureDraftRepository.findByDraftTokenAndExpiresAtAfter(
			draftToken,
			LocalDateTime.now(clock)
		);
	}

	private TreeSet<Long> collectLectureIds(
		ParticipantLectureEntity participantLectureEntity,
		List<ParticipantLectureDraftEntity> drafts
	) {
		TreeSet<Long> lectureIds = new TreeSet<>();

		if (participantLectureEntity != null) {
			addLectureId(lectureIds, participantLectureEntity.getMorningLectureEntity());
			addLectureId(lectureIds, participantLectureEntity.getAfternoonLectureEntity());
		}
		drafts.forEach(draft -> addLectureId(lectureIds, draft.getLectureEntity()));

		return lectureIds;
	}

	private void addLectureId(TreeSet<Long> lectureIds, LectureEntity lectureEntity) {
		if (lectureEntity != null) {
			lectureIds.add(lectureEntity.getId());
		}
	}

	private Map<Long, LectureEntity> lockLecturesById(Collection<Long> lectureIds) {
		Map<Long, LectureEntity> lockedLectures = new HashMap<>();

		for (Long lectureId : lectureIds) {
			LectureEntity lectureEntity = lectureRepository.findByIdForUpdate(lectureId)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 특강입니다."));
			lockedLectures.put(lectureId, lectureEntity);
		}

		return lockedLectures;
	}

	private Map<LectureType, LectureEntity> buildEffectiveLectures(
		ParticipantLectureEntity participantLectureEntity,
		List<ParticipantLectureDraftEntity> activeDrafts
	) {
		Map<LectureType, LectureEntity> effectiveLectures = new EnumMap<>(LectureType.class);

		addExistingLectures(participantLectureEntity, effectiveLectures);
		activeDrafts.forEach(draft -> effectiveLectures.put(draft.getLectureType(), draft.getLectureEntity()));

		return effectiveLectures;
	}

	private Map<LectureType, LectureEntity> buildEffectiveLectures(
		ParticipantLectureEntity participantLectureEntity,
		List<ParticipantLectureDraftEntity> activeDrafts,
		Map<Long, LectureEntity> lockedLectures
	) {
		Map<LectureType, LectureEntity> effectiveLectures = new EnumMap<>(LectureType.class);

		if (participantLectureEntity != null) {
			addLockedLecture(effectiveLectures, LectureType.AM, participantLectureEntity.getMorningLectureEntity(), lockedLectures);
			addLockedLecture(effectiveLectures, LectureType.PM, participantLectureEntity.getAfternoonLectureEntity(), lockedLectures);
		}
		activeDrafts.forEach(draft -> effectiveLectures.put(
			draft.getLectureType(),
			lockedLectures.get(draft.getLectureEntity().getId())
		));

		return effectiveLectures;
	}

	private void addExistingLectures(
		ParticipantLectureEntity participantLectureEntity,
		Map<LectureType, LectureEntity> effectiveLectures
	) {
		if (participantLectureEntity == null) {
			return;
		}
		if (participantLectureEntity.getMorningLectureEntity() != null) {
			effectiveLectures.put(LectureType.AM, participantLectureEntity.getMorningLectureEntity());
		}
		if (participantLectureEntity.getAfternoonLectureEntity() != null) {
			effectiveLectures.put(LectureType.PM, participantLectureEntity.getAfternoonLectureEntity());
		}
	}

	private void addLockedLecture(
		Map<LectureType, LectureEntity> effectiveLectures,
		LectureType lectureType,
		LectureEntity lectureEntity,
		Map<Long, LectureEntity> lockedLectures
	) {
		if (lectureEntity != null) {
			effectiveLectures.put(lectureType, lockedLectures.get(lectureEntity.getId()));
		}
	}

	private RecruitmentEntity resolveRecruitment(
		ParticipantLectureEntity participantLectureEntity,
		Map<LectureType, LectureEntity> effectiveLectures
	) {
		if (participantLectureEntity != null) {
			return participantLectureEntity.getParticipantEntity().getRecruitmentEntity();
		}
		if (!effectiveLectures.isEmpty()) {
			return effectiveLectures.values().iterator().next().getRecruitmentEntity();
		}

		throw new IllegalStateException("유효한 임시점유가 없습니다. 특강을 다시 선택해주세요.");
	}

	private void validateRecruitmentScope(
		Long expectedRecruitmentId,
		RecruitmentEntity recruitmentEntity,
		List<ParticipantLectureDraftEntity> drafts,
		Map<LectureType, LectureEntity> effectiveLectures
	) {
		if (expectedRecruitmentId == null || !recruitmentEntity.getId().equals(expectedRecruitmentId)) {
			throw new IllegalArgumentException("현재 신청 중인 모집과 신청 정보가 일치하지 않습니다.");
		}
		if (drafts.stream().anyMatch(draft -> !draft.getRecruitmentEntity().getId().equals(expectedRecruitmentId))) {
			throw new IllegalArgumentException("서로 다른 모집의 임시점유를 함께 확정할 수 없습니다.");
		}
		if (effectiveLectures.values().stream().anyMatch(
			lecture -> !lecture.getRecruitmentEntity().getId().equals(expectedRecruitmentId)
		)) {
			throw new IllegalArgumentException("서로 다른 모집의 특강을 함께 신청할 수 없습니다.");
		}
	}

	private void validateRegularFinalization(FinalizeContext context) {
		RecruitmentEntity recruitmentEntity = context.recruitmentEntity();

		if (!recruitmentEntity.canAcceptApplications()) {
			throw new IllegalStateException("현재 수강신청을 진행할 수 있는 모집이 아닙니다.");
		}

		context.activeDrafts().forEach(draft -> {
			LectureEntity lectureEntity = context.effectiveLectures().get(draft.getLectureType());

			if (lectureEntity == null || !lectureEntity.isOpen()) {
				throw new IllegalStateException("선택한 특강이 신청 준비중으로 변경되어 최종 신청할 수 없습니다.");
			}
		});
	}

	private boolean hasRequiredLectures(
		RecruitmentQueryService.ParticipantTypeRule rule,
		Map<LectureType, LectureEntity> effectiveLectures
	) {
		return (!rule.canSelectMorningLecture() || effectiveLectures.get(LectureType.AM) != null)
			&& (!rule.canSelectAfternoonLecture() || effectiveLectures.get(LectureType.PM) != null);
	}

	private void applyFinalLecture(
		ParticipantLectureEntity participantLectureEntity,
		LectureType lectureType,
		LectureEntity nextLectureEntity,
		LocalDateTime appliedAt
	) {
		LectureEntity previousLectureEntity = participantLectureEntity.getAppliedLecture(lectureType);

		if (previousLectureEntity != null
			&& nextLectureEntity != null
			&& previousLectureEntity.getId().equals(nextLectureEntity.getId())) {
			return;
		}
		if (previousLectureEntity != null) {
			previousLectureEntity.decreaseParticipantCount();
		}
		if (nextLectureEntity != null) {
			// A valid draft already reserved this seat. Conversion must not count the draft and reject itself.
			nextLectureEntity.forceIncreaseParticipantCount();
			participantLectureEntity.apply(nextLectureEntity, appliedAt);
		}
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

	private record FinalizeContext(
		ParticipantLectureEntity participantLectureEntity,
		List<ParticipantLectureDraftEntity> lockedDrafts,
		List<ParticipantLectureDraftEntity> activeDrafts,
		Map<LectureType, LectureEntity> effectiveLectures,
		RecruitmentEntity recruitmentEntity,
		LocalDateTime now
	) {
	}
}
