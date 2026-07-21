package com.wemisson.career_camp.domain.recruitment.service.policy;

import java.util.List;

import org.springframework.stereotype.Component;

import com.wemisson.career_camp.domain.recruitment.dto.LectureType;
import com.wemisson.career_camp.domain.recruitment.dto.RecruitmentStatus;
import com.wemisson.career_camp.domain.recruitment.entity.LectureEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentParticipantTypeEntity;
import com.wemisson.career_camp.domain.recruitment.repository.LectureRepository;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentChurchRepository;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentParticipantTypeRepository;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RecruitmentStatusPolicy {

	private final RecruitmentRepository recruitmentRepository;
	private final LectureRepository lectureRepository;
	private final RecruitmentChurchRepository recruitmentChurchRepository;
	private final RecruitmentParticipantTypeRepository recruitmentParticipantTypeRepository;

	public void validateStatusChange(
		RecruitmentEntity recruitmentEntity,
		RecruitmentStatus nextStatus
	) {
		if (nextStatus == RecruitmentStatus.OPEN) {
			if (hasOtherOpenOrWaitingRecruitment(recruitmentEntity)) {
				throw new IllegalStateException("모집시작 또는 대기중 상태인 모집이 있어 모집을 시작할 수 없습니다.");
			}
			validateReadyToOpen(recruitmentEntity);
		}
		if (nextStatus == RecruitmentStatus.WAITING && hasOtherOpenOrWaitingRecruitment(recruitmentEntity)) {
			throw new IllegalStateException("모집시작 또는 대기중 상태인 모집이 있어 대기중으로 변경할 수 없습니다.");
		}
	}

	public boolean canOpenAutomatically(RecruitmentEntity recruitmentEntity) {
		return !hasOtherOpenRecruitment(recruitmentEntity)
			&& !hasOtherWaitingRecruitment(recruitmentEntity)
			&& isReadyToOpen(recruitmentEntity);
	}

	public boolean hasOtherWaitingRecruitment(RecruitmentEntity recruitmentEntity) {
		return recruitmentRepository.existsByStatusAndIdNot(RecruitmentStatus.WAITING, recruitmentEntity.getId());
	}

	private void validateReadyToOpen(RecruitmentEntity recruitmentEntity) {
		if (recruitmentChurchRepository.findByRecruitmentEntityOrderBySortOrderAscIdAsc(recruitmentEntity).isEmpty()) {
			throw new IllegalStateException("교회 목록을 1개 이상 등록한 뒤 모집을 시작할 수 있습니다.");
		}

		List<RecruitmentParticipantTypeEntity> selectableRules = findSelectableRules(recruitmentEntity);

		if (selectableRules.isEmpty()) {
			throw new IllegalStateException("신청 가능한 참가자 타입을 1개 이상 설정한 뒤 모집을 시작할 수 있습니다.");
		}

		List<LectureEntity> lectures = lectureRepository.findByRecruitmentEntityOrderByTypeAscSortOrderAscIdAsc(
			recruitmentEntity
		);

		validateRequiredLectures(selectableRules, lectures);
	}

	private boolean isReadyToOpen(RecruitmentEntity recruitmentEntity) {
		if (recruitmentChurchRepository.findByRecruitmentEntityOrderBySortOrderAscIdAsc(recruitmentEntity).isEmpty()) {
			return false;
		}

		List<RecruitmentParticipantTypeEntity> selectableRules = findSelectableRules(recruitmentEntity);

		if (selectableRules.isEmpty()) {
			return false;
		}

		List<LectureEntity> lectures = lectureRepository.findByRecruitmentEntityOrderByTypeAscSortOrderAscIdAsc(
			recruitmentEntity
		);

		return !lectures.isEmpty()
			&& hasRequiredLectures(selectableRules, lectures);
	}

	private List<RecruitmentParticipantTypeEntity> findSelectableRules(RecruitmentEntity recruitmentEntity) {
		return recruitmentParticipantTypeRepository
			.findByRecruitmentEntityOrderByIdAsc(recruitmentEntity)
			.stream()
			.filter(rule -> rule.canSelectMorningLecture() || rule.canSelectAfternoonLecture())
			.toList();
	}

	private void validateRequiredLectures(
		List<RecruitmentParticipantTypeEntity> selectableRules,
		List<LectureEntity> lectures
	) {
		boolean requiresMorningLecture = selectableRules.stream()
			.anyMatch(RecruitmentParticipantTypeEntity::canSelectMorningLecture);
		boolean requiresAfternoonLecture = selectableRules.stream()
			.anyMatch(RecruitmentParticipantTypeEntity::canSelectAfternoonLecture);

		if (requiresMorningLecture && !hasAvailableOpenLecture(lectures, LectureType.AM)) {
			throw new IllegalStateException("신청 가능한 오전 특강을 1개 이상 등록한 뒤 모집을 시작할 수 있습니다.");
		}
		if (requiresAfternoonLecture && !hasAvailableOpenLecture(lectures, LectureType.PM)) {
			throw new IllegalStateException("신청 가능한 오후 특강을 1개 이상 등록한 뒤 모집을 시작할 수 있습니다.");
		}
	}

	private boolean hasRequiredLectures(
		List<RecruitmentParticipantTypeEntity> selectableRules,
		List<LectureEntity> lectures
	) {
		boolean requiresMorningLecture = selectableRules.stream()
			.anyMatch(RecruitmentParticipantTypeEntity::canSelectMorningLecture);
		boolean requiresAfternoonLecture = selectableRules.stream()
			.anyMatch(RecruitmentParticipantTypeEntity::canSelectAfternoonLecture);

		return (!requiresMorningLecture || hasAvailableOpenLecture(lectures, LectureType.AM))
			&& (!requiresAfternoonLecture || hasAvailableOpenLecture(lectures, LectureType.PM));
	}

	private boolean hasAvailableOpenLecture(
		List<LectureEntity> lectures,
		LectureType lectureType
	) {
		return lectures.stream()
			.anyMatch(lecture -> lecture.getType() == lectureType
				&& lecture.isOpen()
				&& lecture.getMaxCapacity() > lecture.getParticipantCount());
	}

	private boolean hasOtherOpenOrWaitingRecruitment(RecruitmentEntity recruitmentEntity) {
		return hasOtherOpenRecruitment(recruitmentEntity) || hasOtherWaitingRecruitment(recruitmentEntity);
	}

	private boolean hasOtherOpenRecruitment(RecruitmentEntity recruitmentEntity) {
		return recruitmentRepository.existsByStatusAndIdNot(RecruitmentStatus.OPEN, recruitmentEntity.getId());
	}
}
