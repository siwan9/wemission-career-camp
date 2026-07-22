package com.wemisson.career_camp.domain.participant.service.draft;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wemisson.career_camp.domain.participant.entity.ParticipantEntity;
import com.wemisson.career_camp.domain.participant.entity.ParticipantLectureDraftEntity;
import com.wemisson.career_camp.domain.participant.repository.ParticipantLectureDraftRepository;
import com.wemisson.career_camp.domain.participant.repository.ParticipantLectureRepository;
import com.wemisson.career_camp.domain.recruitment.dto.LectureType;
import com.wemisson.career_camp.domain.recruitment.entity.LectureEntity;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LectureDraftService {

	private static final long DRAFT_TTL_MINUTES = 3L;

	private final ParticipantLectureDraftRepository participantLectureDraftRepository;
	private final ParticipantLectureRepository participantLectureRepository;
	private final LectureCapacityService lectureCapacityService;
	private final Clock clock;

	public ParticipantEntity lockParticipantApplication(Long participantLectureId) {
		if (participantLectureId == null) {
			return null;
		}

		return participantLectureRepository.findByIdForUpdate(participantLectureId)
			.orElseThrow(() -> new IllegalArgumentException("신청 정보를 찾을 수 없습니다."))
			.getParticipantEntity();
	}

	public List<ParticipantLectureDraftEntity> lockDrafts(String draftToken) {
		if (draftToken == null || draftToken.isBlank()) {
			throw new IllegalArgumentException("임시점유 정보를 찾을 수 없습니다. 다시 신청해주세요.");
		}

		return participantLectureDraftRepository.findByDraftTokenForUpdate(draftToken);
	}

	public DraftSelection hold(
		ParticipantEntity participantEntity,
		LectureEntity lectureEntity,
		String draftToken,
		boolean allowFull,
		List<ParticipantLectureDraftEntity> lockedDrafts
	) {
		LocalDateTime now = LocalDateTime.now(clock);
		ParticipantLectureDraftEntity existingDraft = lockedDrafts.stream()
			.filter(draft -> draft.getLectureType() == lectureEntity.getType())
			.findFirst()
			.orElse(null);
		boolean alreadyHeld = existingDraft != null
			&& existingDraft.getLectureEntity().getId().equals(lectureEntity.getId())
			&& existingDraft.getExpiresAt().isAfter(now);
		boolean hasActiveDifferentDraft = existingDraft != null
			&& !existingDraft.getLectureEntity().getId().equals(lectureEntity.getId())
			&& existingDraft.getExpiresAt().isAfter(now);

		if (hasActiveDifferentDraft) {
			throw new IllegalStateException("같은 시간대에 이미 선택한 특강이 있습니다. 먼저 선택 해제 후 변경해주세요.");
		}

		if (!allowFull && !alreadyHeld) {
			lectureCapacityService.validateAvailable(lectureEntity, now);
		}

		LocalDateTime expiresAt = now.plusMinutes(DRAFT_TTL_MINUTES);

		if (existingDraft == null) {
			participantLectureDraftRepository.save(
				ParticipantLectureDraftEntity.create(
					draftToken,
					participantEntity,
					lectureEntity.getRecruitmentEntity(),
					lectureEntity,
					expiresAt,
					now
				)
			);
		} else if (!alreadyHeld) {
			existingDraft.updateLecture(lectureEntity, expiresAt, now);
		}

		return new DraftSelection(
			lectureEntity.getId(),
			lectureEntity.getType(),
			lectureCapacityService.getRemainingCapacity(lectureEntity, now),
			true
		);
	}

	public DraftSelection release(
		LectureEntity lectureEntity,
		List<ParticipantLectureDraftEntity> lockedDrafts
	) {
		LocalDateTime now = LocalDateTime.now(clock);

		lockedDrafts.stream()
			.filter(draft -> draft.getLectureType() == lectureEntity.getType())
			.filter(draft -> draft.getLectureEntity().getId().equals(lectureEntity.getId()))
			.findFirst()
			.ifPresent(participantLectureDraftRepository::delete);

		return new DraftSelection(
			lectureEntity.getId(),
			lectureEntity.getType(),
			lectureCapacityService.getRemainingCapacity(lectureEntity, now),
			false
		);
	}

	public List<ParticipantLectureDraftEntity> findActiveDrafts(String draftToken) {
		if (draftToken == null) {
			return List.of();
		}

		return participantLectureDraftRepository.findByDraftTokenAndExpiresAtAfter(
			draftToken,
			LocalDateTime.now(clock)
		);
	}

	@Transactional
	public void releaseAll(String draftToken) {
		if (draftToken == null) {
			return;
		}

		participantLectureDraftRepository.deleteAll(lockDrafts(draftToken));
	}

	public record DraftSelection(
		Long lectureId,
		LectureType lectureType,
		int remainingCapacity,
		boolean held
	) {
	}
}
