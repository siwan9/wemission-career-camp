package com.wemisson.career_camp.domain.participant.service.command;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wemisson.career_camp.domain.participant.dto.ParticipantLookupResult;
import com.wemisson.career_camp.domain.participant.dto.ParticipantCreateRequest;
import com.wemisson.career_camp.domain.participant.entity.ParticipantEntity;
import com.wemisson.career_camp.domain.participant.entity.ParticipantLectureDraftEntity;
import com.wemisson.career_camp.domain.participant.entity.ParticipantLectureEntity;
import com.wemisson.career_camp.domain.participant.repository.ParticipantLectureDraftRepository;
import com.wemisson.career_camp.domain.participant.repository.ParticipantLectureRepository;
import com.wemisson.career_camp.domain.participant.repository.ParticipantRepository;
import com.wemisson.career_camp.domain.recruitment.entity.LectureEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;
import com.wemisson.career_camp.domain.recruitment.repository.LectureRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ParticipantLookupService {

	private final ParticipantRepository participantRepository;
	private final ParticipantLectureRepository participantLectureRepository;
	private final ParticipantLectureDraftRepository participantLectureDraftRepository;
	private final LectureRepository lectureRepository;

	@Transactional(readOnly = true)
	public List<ParticipantLookupResult> lookup(
		RecruitmentEntity recruitmentEntity,
		String name,
		Long churchId,
		String phoneNumber
	) {
		List<ParticipantEntity> participants = participantRepository
			.findByRecruitmentEntityAndNameAndRecruitmentChurchEntityIdAndPhoneNumberOrderByIdAsc(
				recruitmentEntity,
				name,
				churchId,
				phoneNumber
			);

		if (participants.isEmpty()) {
			return List.of();
		}

		return participantLectureRepository.findByParticipantEntityIn(participants)
			.stream()
			.map(ParticipantLookupResult::from)
			.toList();
	}

	@Transactional
	public void deleteParticipantLecture(Long participantLectureId) {
		ParticipantLectureEntity participantLectureEntity = findParticipantLectureForUpdate(participantLectureId);
		ParticipantEntity participantEntity = participantLectureEntity.getParticipantEntity();
		List<ParticipantLectureDraftEntity> lockedDrafts = participantLectureDraftRepository
			.findByParticipantEntityForUpdate(participantEntity);
		Map<Long, LectureEntity> lockedLectures = lockRelatedLectures(participantLectureEntity, lockedDrafts);

		decreaseAppliedLecture(lockedLectures, participantLectureEntity.getMorningLectureEntity());
		decreaseAppliedLecture(lockedLectures, participantLectureEntity.getAfternoonLectureEntity());
		participantLectureDraftRepository.deleteAll(lockedDrafts);
		participantLectureRepository.delete(participantLectureEntity);
		participantRepository.delete(participantEntity);
	}

	private Map<Long, LectureEntity> lockRelatedLectures(
		ParticipantLectureEntity participantLectureEntity,
		List<ParticipantLectureDraftEntity> drafts
	) {
		TreeSet<Long> lectureIds = new TreeSet<>();
		Map<Long, LectureEntity> lockedLectures = new java.util.HashMap<>();

		addLectureId(lectureIds, participantLectureEntity.getMorningLectureEntity());
		addLectureId(lectureIds, participantLectureEntity.getAfternoonLectureEntity());
		drafts.forEach(draft -> addLectureId(lectureIds, draft.getLectureEntity()));

		for (Long lectureId : lectureIds) {
			lockedLectures.put(
				lectureId,
				lectureRepository.findByIdForUpdate(lectureId)
					.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 특강입니다."))
			);
		}

		return lockedLectures;
	}

	private void decreaseAppliedLecture(
		Map<Long, LectureEntity> lockedLectures,
		LectureEntity lectureEntity
	) {
		if (lectureEntity != null) {
			lockedLectures.get(lectureEntity.getId()).decreaseParticipantCount();
		}
	}

	private void addLectureId(
		TreeSet<Long> lectureIds,
		LectureEntity lectureEntity
	) {
		if (lectureEntity == null) {
			return;
		}

		lectureIds.add(lectureEntity.getId());
	}

	private ParticipantLectureEntity findParticipantLecture(Long participantLectureId) {
		return participantLectureRepository.findById(participantLectureId)
			.orElseThrow(() -> new IllegalArgumentException("신청 정보를 찾을 수 없습니다."));
	}

	@Transactional(readOnly = true)
	public LookupEditTarget findLookupEditTarget(Long participantLectureId) {
		ParticipantLectureEntity participantLectureEntity = participantLectureRepository
			.findByIdWithLookupEditRelations(participantLectureId)
			.orElseThrow(() -> new IllegalArgumentException("신청 정보를 찾을 수 없습니다."));
		ParticipantEntity participantEntity = participantLectureEntity.getParticipantEntity();

		return new LookupEditTarget(
			participantLectureEntity.getId(),
			participantEntity.getId(),
			toCreateRequest(participantLectureEntity),
			participantEntity.getRecruitmentEntity().isOpen()
		);
	}

	@Transactional(readOnly = true)
	public RegistrationCompleteView findRegistrationCompleteView(Long participantLectureId) {
		return RegistrationCompleteView.from(findParticipantLecture(participantLectureId));
	}

	private ParticipantLectureEntity findParticipantLectureForUpdate(Long participantLectureId) {
		return participantLectureRepository.findByIdForUpdate(participantLectureId)
			.orElseThrow(() -> new IllegalArgumentException("신청 정보를 찾을 수 없습니다."));
	}

	public ParticipantCreateRequest toCreateRequest(ParticipantLectureEntity participantLectureEntity) {
		ParticipantEntity participant = participantLectureEntity.getParticipantEntity();

		return new ParticipantCreateRequest(
			participant.getName(),
			participant.getType().getId(),
			participant.getRecruitmentChurchEntity().getId(),
			participant.getPhoneNumber()
		);
	}

	public record RegistrationCompleteView(
		Long participantLectureId,
		String recruitmentName,
		String participantName,
		String participantTypeName,
		String churchName,
		String phoneNumber,
		String morningLectureName,
		String morningLectureJob,
		String afternoonLectureName,
		String afternoonLectureJob,
		boolean recruitmentOpen
	) {
		private static RegistrationCompleteView from(ParticipantLectureEntity participantLectureEntity) {
			ParticipantEntity participant = participantLectureEntity.getParticipantEntity();
			LectureEntity morningLecture = participantLectureEntity.getMorningLectureEntity();
			LectureEntity afternoonLecture = participantLectureEntity.getAfternoonLectureEntity();

			return new RegistrationCompleteView(
				participantLectureEntity.getId(),
				participant.getRecruitmentEntity().getName(),
				participant.getName(),
				participant.getType().isStudent() ? "학생" : "교사",
				participant.getRecruitmentChurchEntity().getName(),
				participant.getPhoneNumber(),
				getLectureName(morningLecture),
				getLectureJob(morningLecture),
				getLectureName(afternoonLecture),
				getLectureJob(afternoonLecture),
				participant.getRecruitmentEntity().isOpen()
			);
		}

		private static String getLectureName(LectureEntity lectureEntity) {
			if (lectureEntity == null) {
				return "미신청";
			}

			return lectureEntity.getSpeakerName() + " 강사";
		}

		private static String getLectureJob(LectureEntity lectureEntity) {
			if (lectureEntity == null) {
				return "";
			}

			return lectureEntity.getSpeakerJob();
		}
	}

	public record LookupEditTarget(
		Long participantLectureId,
		Long participantId,
		ParticipantCreateRequest request,
		boolean recruitmentOpen
	) {
	}
}
