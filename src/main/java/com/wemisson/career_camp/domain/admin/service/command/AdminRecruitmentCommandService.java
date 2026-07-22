package com.wemisson.career_camp.domain.admin.service.command;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wemisson.career_camp.domain.participant.dto.ParticipantCreateRequest;
import com.wemisson.career_camp.domain.participant.entity.ParticipantEntity;
import com.wemisson.career_camp.domain.participant.entity.ParticipantLectureEntity;
import com.wemisson.career_camp.domain.participant.repository.ParticipantLectureDraftRepository;
import com.wemisson.career_camp.domain.participant.repository.ParticipantLectureRepository;
import com.wemisson.career_camp.domain.participant.repository.ParticipantRepository;
import com.wemisson.career_camp.domain.participant.repository.ParticipantTypeRepository;
import com.wemisson.career_camp.domain.participant.service.command.ParticipantLookupService;
import com.wemisson.career_camp.domain.recruitment.dto.LectureType;
import com.wemisson.career_camp.domain.recruitment.dto.RecruitmentStatus;
import com.wemisson.career_camp.domain.recruitment.entity.LectureEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentChurchEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentParticipantTypeEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentParticipantTypeFixedLectureEntity;
import com.wemisson.career_camp.domain.recruitment.repository.LectureRepository;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentChurchRepository;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentParticipantTypeFixedLectureRepository;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentParticipantTypeRepository;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentRepository;
import com.wemisson.career_camp.domain.recruitment.service.policy.RecruitmentStatusPolicy;
import com.wemisson.career_camp.domain.recruitment.service.query.LectureCatalogQueryService;
import com.wemisson.career_camp.domain.recruitment.service.query.LectureQueryService;
import com.wemisson.career_camp.domain.recruitment.service.query.RecruitmentQueryService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminRecruitmentCommandService {

	private final RecruitmentRepository recruitmentRepository;
	private final ParticipantRepository participantRepository;
	private final ParticipantLectureRepository participantLectureRepository;
	private final ParticipantLectureDraftRepository participantLectureDraftRepository;
	private final ParticipantTypeRepository participantTypeRepository;
	private final LectureRepository lectureRepository;
	private final RecruitmentChurchRepository recruitmentChurchRepository;
	private final RecruitmentParticipantTypeRepository recruitmentParticipantTypeRepository;
	private final RecruitmentParticipantTypeFixedLectureRepository fixedLectureRepository;
	private final ParticipantLookupService participantLookupService;
	private final RecruitmentQueryService recruitmentService;
	private final LectureQueryService lectureService;
	private final LectureCatalogQueryService lectureCatalogQueryService;
	private final RecruitmentStatusPolicy recruitmentStatusPolicy;
	private final Clock clock;

	@Transactional
	public void createRecruitment(
		String name,
		String description,
		String notice,
		LocalDateTime startAt,
		LocalDateTime endAt
	) {
		LocalDateTime now = LocalDateTime.now(clock);
		RecruitmentEntity recruitmentEntity = recruitmentRepository.save(
			RecruitmentEntity.create(
				name,
				description,
				notice,
				startAt,
				endAt,
				RecruitmentStatus.CLOSED,
				now
			)
		);
		createParticipantTypeRules(recruitmentEntity);
		recruitmentService.evictRecruitmentCaches(recruitmentEntity.getId());
	}

	@Transactional
	public void deleteRecruitment(Long recruitmentId) {
		RecruitmentEntity recruitmentEntity = recruitmentRepository.findByIdForUpdate(recruitmentId)
			.orElseThrow(() -> new IllegalArgumentException("모집을 찾을 수 없습니다."));

		if (!recruitmentEntity.isClosed()) {
			throw new IllegalStateException("모집중 또는 대기중인 모집은 종료한 뒤 삭제해주세요.");
		}

		participantLectureDraftRepository.deleteByRecruitmentEntity(recruitmentEntity);
		participantLectureRepository.deleteByParticipantEntityRecruitmentEntity(recruitmentEntity);
		participantRepository.deleteByRecruitmentEntity(recruitmentEntity);
		fixedLectureRepository.deleteByRecruitmentEntity(recruitmentEntity);
		lectureRepository.deleteByRecruitmentEntity(recruitmentEntity);
		recruitmentChurchRepository.deleteByRecruitmentEntity(recruitmentEntity);
		recruitmentParticipantTypeRepository.deleteByRecruitmentEntity(recruitmentEntity);
		recruitmentRepository.delete(recruitmentEntity);
		recruitmentService.evictRecruitmentCaches(recruitmentId);
		lectureService.evictLectureStaticCache(recruitmentId);
		lectureCatalogQueryService.evictFixedLectureCache(recruitmentId);
	}

	@Transactional
	public void deleteParticipant(
		Long recruitmentId,
		Long participantId
	) {
		ParticipantEntity participantEntity = findParticipant(recruitmentId, participantId);

		ParticipantLectureEntity participantLectureEntity = participantLectureRepository
			.findByParticipantEntity(participantEntity)
			.orElse(null);

		if (participantLectureEntity != null) {
			participantLookupService.deleteParticipantLecture(participantLectureEntity.getId());
			return;
		}

		participantLectureDraftRepository.deleteAll(
			participantLectureDraftRepository.findByParticipantEntityForUpdate(participantEntity)
		);
		participantRepository.delete(participantEntity);
	}

	@Transactional
	public void updateRecruitment(
		Long recruitmentId,
		String name,
		String description,
		String notice,
		LocalDateTime startAt,
		LocalDateTime endAt,
		RecruitmentStatus status
	) {
		RecruitmentEntity recruitmentEntity = findRecruitmentForStatusChange(recruitmentId);
		LocalDateTime now = LocalDateTime.now(clock);

		validateRecruitmentStatusChange(recruitmentEntity, status);
		recruitmentEntity.update(name, description, notice, startAt, endAt, status, now);
		recruitmentService.evictRecruitmentCaches(recruitmentId);
	}

	@Transactional
	public RecruitmentStatus changeRecruitmentStatus(
		Long recruitmentId,
		RecruitmentStatus nextStatus
	) {
		RecruitmentEntity recruitmentEntity = findRecruitmentForStatusChange(recruitmentId);

		if (recruitmentEntity.getStatus() == nextStatus) {
			return nextStatus;
		}

		validateRecruitmentStatusChange(recruitmentEntity, nextStatus);
		recruitmentEntity.changeStatus(nextStatus, LocalDateTime.now(clock));
		recruitmentService.evictRecruitmentCaches(recruitmentId);

		return nextStatus;
	}

	@Transactional
	public RecruitmentStatus toggleRecruitmentStatus(Long recruitmentId) {
		RecruitmentEntity recruitmentEntity = findRecruitmentForStatusChange(recruitmentId);
		RecruitmentStatus nextStatus = getNextRecruitmentStatus(recruitmentEntity);

		validateRecruitmentStatusChange(recruitmentEntity, nextStatus);
		recruitmentEntity.changeStatus(nextStatus, LocalDateTime.now(clock));
		recruitmentService.evictRecruitmentCaches(recruitmentId);

		return nextStatus;
	}

	@Transactional
	public void updateParticipantTypeRule(
		Long recruitmentId,
		Long ruleId,
		boolean canSelectMorningLecture,
		boolean canSelectAfternoonLecture,
		Long fixedMorningLectureId,
		Long fixedAfternoonLectureId
	) {
		RecruitmentEntity recruitmentEntity = findRecruitment(recruitmentId);
		RecruitmentParticipantTypeEntity rule = findParticipantTypeRule(recruitmentEntity, ruleId);

		rule.updateLecturePermission(canSelectMorningLecture, canSelectAfternoonLecture);
		synchronizeFixedLectures(
			recruitmentEntity,
			rule,
			fixedMorningLectureId,
			fixedAfternoonLectureId
		);
		recruitmentService.evictParticipantTypeRuleCache(recruitmentId);
		lectureCatalogQueryService.evictFixedLectureCache(recruitmentId);
	}

	@Transactional
	public void updateLecture(
		Long recruitmentId,
		Long lectureId,
		String speakerName,
		String speakerJob,
		String description,
		LectureType type,
		boolean isOpen,
		Integer maxCapacity
	) {
		RecruitmentEntity recruitmentEntity = findRecruitment(recruitmentId);
		LectureEntity lectureEntity = findLectureForUpdate(recruitmentEntity, lectureId);
		validateMaxCapacity(maxCapacity);
		long activeDraftCount = countActiveDrafts(lectureEntity);
		validateLectureTypeChange(lectureEntity, type, activeDraftCount);
		validateCapacityReduction(lectureEntity, maxCapacity, activeDraftCount);
		LectureType oldType = lectureEntity.getType();

		lectureEntity.update(speakerName, speakerJob, description, type, isOpen, maxCapacity);
		resequenceLectures(recruitmentEntity, oldType);
		resequenceLectures(recruitmentEntity, type);
		lectureService.evictLectureStaticCache(recruitmentId);
	}

	@Transactional
	public void createLecture(
		Long recruitmentId,
		String speakerName,
		String speakerJob,
		String description,
		LectureType type,
		boolean isOpen,
		Integer maxCapacity
	) {
		RecruitmentEntity recruitmentEntity = findRecruitment(recruitmentId);
		validateMaxCapacity(maxCapacity);
		int targetOrder = lectureRepository
			.findByRecruitmentEntityAndTypeOrderBySortOrderAscIdAsc(recruitmentEntity, type)
			.size() + 1;

		lectureRepository.save(
			LectureEntity.create(
				recruitmentEntity,
				speakerName,
				speakerJob,
				description,
				type,
				isOpen,
				maxCapacity,
				targetOrder
			)
		);
		resequenceLectures(recruitmentEntity, type);
		lectureService.evictLectureStaticCache(recruitmentId);
	}

	@Transactional
	public void deleteLecture(
		Long recruitmentId,
		Long lectureId
	) {
		RecruitmentEntity recruitmentEntity = findRecruitment(recruitmentId);
		LectureEntity lectureEntity = findLectureForUpdate(recruitmentEntity, lectureId);

		if (lectureEntity.getParticipantCount() > 0 || countActiveDrafts(lectureEntity) > 0) {
			throw new IllegalStateException("신청자 또는 임시점유가 있는 강의는 삭제할 수 없습니다.");
		}
		if (fixedLectureRepository.existsByLectureEntity(lectureEntity)) {
			throw new IllegalStateException("고정 강좌로 지정된 강의는 참가자 타입 설정을 먼저 변경한 뒤 삭제해주세요.");
		}

		LectureType deletedType = lectureEntity.getType();
		lectureRepository.delete(lectureEntity);
		resequenceLectures(recruitmentEntity, deletedType);
		lectureService.evictLectureStaticCache(recruitmentId);
	}

	@Transactional
	public void reorderLectures(
		Long recruitmentId,
		List<Long> lectureIds
	) {
		RecruitmentEntity recruitmentEntity = findRecruitment(recruitmentId);
		Map<Long, LectureEntity> lectureById = lectureRepository
			.findByRecruitmentEntityOrderByTypeAscSortOrderAscIdAsc(recruitmentEntity)
			.stream()
			.collect(Collectors.toMap(LectureEntity::getId, Function.identity()));
		LectureType targetType = null;

		for (int index = 0; index < lectureIds.size(); index++) {
			LectureEntity lectureEntity = lectureById.get(lectureIds.get(index));

			if (lectureEntity == null) {
				throw new IllegalArgumentException("해당 모집의 강의가 아닙니다.");
			}
			if (targetType == null) {
				targetType = lectureEntity.getType();
			}
			if (lectureEntity.getType() != targetType) {
				throw new IllegalArgumentException("서로 다른 시간대의 강의 순서는 함께 변경할 수 없습니다.");
			}

			lectureEntity.changeSortOrder(index + 1);
		}
		if (targetType != null) {
			resequenceLectures(recruitmentEntity, targetType);
			lectureService.evictLectureStaticCache(recruitmentId);
		}
	}

	@Transactional
	public void createChurch(
		Long recruitmentId,
		String name
	) {
		RecruitmentEntity recruitmentEntity = findRecruitment(recruitmentId);
		List<RecruitmentChurchEntity> churches = recruitmentChurchRepository
			.findByRecruitmentEntityOrderBySortOrderAscIdAsc(recruitmentEntity);
		int targetOrder = churches.size() + 1;

		recruitmentChurchRepository.save(
			RecruitmentChurchEntity.create(recruitmentEntity, name, targetOrder)
		);
		resequenceChurches(recruitmentEntity);
		recruitmentService.evictChurchCache(recruitmentId);
	}

	@Transactional
	public void reorderChurches(
		Long recruitmentId,
		List<Long> churchIds
	) {
		RecruitmentEntity recruitmentEntity = findRecruitment(recruitmentId);
		Map<Long, RecruitmentChurchEntity> churchById = recruitmentChurchRepository
			.findByRecruitmentEntityOrderBySortOrderAscIdAsc(recruitmentEntity)
			.stream()
			.collect(Collectors.toMap(RecruitmentChurchEntity::getId, Function.identity()));

		for (int index = 0; index < churchIds.size(); index++) {
			RecruitmentChurchEntity churchEntity = churchById.get(churchIds.get(index));

			if (churchEntity == null) {
				throw new IllegalArgumentException("해당 모집의 교회가 아닙니다.");
			}

			churchEntity.changeSortOrder(index + 1);
		}
		resequenceChurches(recruitmentEntity);
		recruitmentService.evictChurchCache(recruitmentId);
	}

	@Transactional
	public void updateChurch(
		Long recruitmentId,
		Long churchId,
		String name,
		Integer sortOrder
	) {
		RecruitmentEntity recruitmentEntity = findRecruitment(recruitmentId);
		RecruitmentChurchEntity churchEntity = findChurch(recruitmentEntity, churchId);

		churchEntity.update(name, sortOrder);
		resequenceChurches(recruitmentEntity);
		recruitmentService.evictChurchCache(recruitmentId);
	}

	@Transactional
	public void deleteChurch(
		Long recruitmentId,
		Long churchId
	) {
		RecruitmentEntity recruitmentEntity = findRecruitment(recruitmentId);
		RecruitmentChurchEntity churchEntity = findChurch(recruitmentEntity, churchId);

		if (participantRepository.countByRecruitmentChurchEntity(churchEntity) > 0) {
			throw new IllegalStateException("신청자가 있는 교회는 삭제할 수 없습니다.");
		}

		recruitmentChurchRepository.delete(churchEntity);
		resequenceChurches(recruitmentEntity);
		recruitmentService.evictChurchCache(recruitmentId);
	}

	@Transactional(readOnly = true)
	public Long findParticipantId(
		Long recruitmentId,
		Long participantId
	) {
		return findParticipant(recruitmentId, participantId).getId();
	}

	@Transactional(readOnly = true)
	public Long findRecruitmentId(Long recruitmentId) {
		return findRecruitment(recruitmentId).getId();
	}

	@Transactional
	public LectureEditSession findOrCreateLectureEditSession(
		Long recruitmentId,
		Long participantId
	) {
		ParticipantEntity participantEntity = findParticipant(recruitmentId, participantId);
		ParticipantLectureEntity participantLectureEntity = participantLectureRepository
			.findByParticipantEntity(participantEntity)
			.orElseGet(() -> participantLectureRepository.save(
				ParticipantLectureEntity.create(participantEntity)
			));

		return new LectureEditSession(
			participantLookupService.toCreateRequest(participantLectureEntity),
			participantLectureEntity.getId()
		);
	}

	private RecruitmentStatus getNextRecruitmentStatus(RecruitmentEntity recruitmentEntity) {
		if (recruitmentEntity.isClosed()) {
			return RecruitmentStatus.WAITING;
		}
		if (recruitmentEntity.isWaiting()) {
			return RecruitmentStatus.OPEN;
		}

		return RecruitmentStatus.CLOSED;
	}

	private void validateRecruitmentStatusChange(
		RecruitmentEntity recruitmentEntity,
		RecruitmentStatus nextStatus
	) {
		recruitmentStatusPolicy.validateStatusChange(recruitmentEntity, nextStatus);
	}

	private void createParticipantTypeRules(RecruitmentEntity recruitmentEntity) {
		participantTypeRepository.findAll()
			.forEach(participantType -> recruitmentParticipantTypeRepository.save(
				RecruitmentParticipantTypeEntity.create(
					recruitmentEntity,
					participantType,
					false,
					false
				)
			));
	}

	private RecruitmentEntity findRecruitment(Long recruitmentId) {
		return recruitmentRepository.findById(recruitmentId)
			.orElseThrow(() -> new IllegalArgumentException("모집을 찾을 수 없습니다."));
	}

	private RecruitmentEntity findRecruitmentForStatusChange(Long recruitmentId) {
		return recruitmentRepository.findAllForUpdateOrderByIdAsc()
			.stream()
			.filter(recruitment -> recruitment.getId().equals(recruitmentId))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("모집을 찾을 수 없습니다."));
	}

	private ParticipantEntity findParticipant(
		Long recruitmentId,
		Long participantId
	) {
		RecruitmentEntity recruitmentEntity = findRecruitment(recruitmentId);
		ParticipantEntity participantEntity = participantRepository.findById(participantId)
			.orElseThrow(() -> new IllegalArgumentException("참가자를 찾을 수 없습니다."));

		if (!participantEntity.getRecruitmentEntity().getId().equals(recruitmentEntity.getId())) {
			throw new IllegalArgumentException("해당 모집의 참가자가 아닙니다.");
		}

		return participantEntity;
	}

	private LectureEntity findLecture(
		RecruitmentEntity recruitmentEntity,
		Long lectureId
	) {
		LectureEntity lectureEntity = lectureRepository.findById(lectureId)
			.orElseThrow(() -> new IllegalArgumentException("강의를 찾을 수 없습니다."));

		if (!lectureEntity.getRecruitmentEntity().getId().equals(recruitmentEntity.getId())) {
			throw new IllegalArgumentException("해당 모집의 강의가 아닙니다.");
		}

		return lectureEntity;
	}

	private LectureEntity findLectureForUpdate(
		RecruitmentEntity recruitmentEntity,
		Long lectureId
	) {
		LectureEntity lectureEntity = lectureRepository.findByIdForUpdate(lectureId)
			.orElseThrow(() -> new IllegalArgumentException("강의를 찾을 수 없습니다."));

		if (!lectureEntity.getRecruitmentEntity().getId().equals(recruitmentEntity.getId())) {
			throw new IllegalArgumentException("해당 모집의 강의가 아닙니다.");
		}

		return lectureEntity;
	}

	private RecruitmentChurchEntity findChurch(
		RecruitmentEntity recruitmentEntity,
		Long churchId
	) {
		return recruitmentChurchRepository.findByIdAndRecruitmentEntity(churchId, recruitmentEntity)
			.orElseThrow(() -> new IllegalArgumentException("교회를 찾을 수 없습니다."));
	}

	private RecruitmentParticipantTypeEntity findParticipantTypeRule(
		RecruitmentEntity recruitmentEntity,
		Long ruleId
	) {
		RecruitmentParticipantTypeEntity rule = recruitmentParticipantTypeRepository.findByIdForUpdate(ruleId)
			.orElseThrow(() -> new IllegalArgumentException("참가자 타입 설정을 찾을 수 없습니다."));

		if (!rule.getRecruitmentEntity().getId().equals(recruitmentEntity.getId())) {
			throw new IllegalArgumentException("해당 모집의 참가자 타입 설정이 아닙니다.");
		}

		return rule;
	}

	private void resequenceChurches(RecruitmentEntity recruitmentEntity) {
		List<RecruitmentChurchEntity> churches = recruitmentChurchRepository
			.findByRecruitmentEntityOrderBySortOrderAscIdAsc(recruitmentEntity);

		for (int index = 0; index < churches.size(); index++) {
			churches.get(index).changeSortOrder(index + 1);
		}
	}

	private void resequenceLectures(
		RecruitmentEntity recruitmentEntity,
		LectureType type
	) {
		List<LectureEntity> lectures = lectureRepository
			.findByRecruitmentEntityAndTypeOrderBySortOrderAscIdAsc(recruitmentEntity, type);

		for (int index = 0; index < lectures.size(); index++) {
			lectures.get(index).changeSortOrder(index + 1);
		}
	}

	private void validateMaxCapacity(Integer maxCapacity) {
		if (maxCapacity == null || maxCapacity < 0) {
			throw new IllegalArgumentException("강의 정원은 0명 이상으로 입력해주세요.");
		}
	}

	private void validateCapacityReduction(
		LectureEntity lectureEntity,
		Integer nextMaxCapacity,
		long activeDraftCount
	) {
		if (nextMaxCapacity >= lectureEntity.getMaxCapacity()) {
			return;
		}

		long occupiedCapacity = lectureEntity.getParticipantCount() + activeDraftCount;

		if (nextMaxCapacity < occupiedCapacity) {
			throw new IllegalStateException(
				"현재 신청자와 임시점유 인원을 합쳐 " + occupiedCapacity + "명이 있어 정원을 "
					+ occupiedCapacity + "명 미만으로 줄일 수 없습니다."
			);
		}
	}

	private long countActiveDrafts(LectureEntity lectureEntity) {
		return participantLectureDraftRepository.countByLectureEntityAndExpiresAtAfter(
			lectureEntity,
			LocalDateTime.now(clock)
		);
	}

	private void validateLectureTypeChange(
		LectureEntity lectureEntity,
		LectureType nextType,
		long activeDraftCount
	) {
		if (lectureEntity.getType() == nextType) {
			return;
		}
		if (lectureEntity.getParticipantCount() > 0 || activeDraftCount > 0) {
			throw new IllegalStateException("신청자 또는 임시점유가 있는 강의의 시간대는 변경할 수 없습니다.");
		}
		if (fixedLectureRepository.existsByLectureEntity(lectureEntity)) {
			throw new IllegalStateException("고정 강좌로 지정된 강의는 참가자 타입 설정을 먼저 변경한 뒤 시간대를 바꿔주세요.");
		}
	}

	private void synchronizeFixedLectures(
		RecruitmentEntity recruitmentEntity,
		RecruitmentParticipantTypeEntity rule,
		Long fixedMorningLectureId,
		Long fixedAfternoonLectureId
	) {
		List<RecruitmentParticipantTypeFixedLectureEntity> existingFixedLectures = fixedLectureRepository
			.findByRecruitmentParticipantTypeEntityOrderByLectureTypeAscIdAsc(rule);

		if (!rule.usesFixedLectures()) {
			fixedLectureRepository.deleteAll(existingFixedLectures);
			return;
		}
		if (fixedMorningLectureId == null || fixedAfternoonLectureId == null) {
			throw new IllegalArgumentException("직접 선택이 불가능한 참가자 타입은 고정 오전·오후 강좌를 모두 선택해주세요.");
		}

		synchronizeFixedLecture(
			recruitmentEntity,
			rule,
			existingFixedLectures,
			LectureType.AM,
			fixedMorningLectureId
		);
		synchronizeFixedLecture(
			recruitmentEntity,
			rule,
			existingFixedLectures,
			LectureType.PM,
			fixedAfternoonLectureId
		);
	}

	private void synchronizeFixedLecture(
		RecruitmentEntity recruitmentEntity,
		RecruitmentParticipantTypeEntity rule,
		List<RecruitmentParticipantTypeFixedLectureEntity> existingFixedLectures,
		LectureType lectureType,
		Long lectureId
	) {
		LectureEntity lectureEntity = findLectureForUpdate(recruitmentEntity, lectureId);

		if (lectureEntity.getType() != lectureType) {
			throw new IllegalArgumentException(
				lectureType == LectureType.AM
					? "고정 오전 강좌에는 오전 강의를 선택해주세요."
					: "고정 오후 강좌에는 오후 강의를 선택해주세요."
			);
		}

		RecruitmentParticipantTypeFixedLectureEntity existingFixedLecture = existingFixedLectures.stream()
			.filter(fixedLecture -> fixedLecture.getLectureType() == lectureType)
			.findFirst()
			.orElse(null);

		if (existingFixedLecture == null) {
			fixedLectureRepository.save(
				RecruitmentParticipantTypeFixedLectureEntity.create(rule, lectureEntity)
			);
			return;
		}

		existingFixedLecture.changeLecture(lectureEntity);
	}

	public record LectureEditSession(
		ParticipantCreateRequest request,
		Long participantLectureId
	) {
	}
}
