package com.wemisson.career_camp.domain.recruitment.service.query;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wemisson.career_camp.common.transaction.AfterCommitExecutor;
import com.wemisson.career_camp.domain.participant.repository.ParticipantLectureDraftRepository;
import com.wemisson.career_camp.domain.recruitment.dto.LectureType;
import com.wemisson.career_camp.domain.recruitment.entity.LectureEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;
import com.wemisson.career_camp.domain.recruitment.repository.LectureRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LectureQueryService {

	private final LectureRepository lectureRepository;
	private final ParticipantLectureDraftRepository participantLectureDraftRepository;
	private final AfterCommitExecutor afterCommitExecutor;
	private final Clock clock;
	private final Cache<Long, LectureStaticCache> lectureStaticCacheByRecruitmentId = Caffeine.newBuilder()
		.maximumSize(128)
		.build();

	@Transactional(readOnly = true)
	public List<LectureView> findOpenLectures(RecruitmentEntity recruitmentEntity, LectureType type) {
		return findLectures(recruitmentEntity, type == LectureType.AM, type == LectureType.PM)
			.lecturesByType(type);
	}

	@Transactional(readOnly = true)
	public LectureSelection findLectures(
		RecruitmentEntity recruitmentEntity,
		boolean includeMorning,
		boolean includeAfternoon
	) {
		if (!includeMorning && !includeAfternoon) {
			return new LectureSelection(List.of(), List.of());
		}

		List<LectureStaticView> staticLectures = findStaticLectures(recruitmentEntity);
		LocalDateTime now = LocalDateTime.now(clock);
		Map<Long, LectureRepository.LectureAvailability> availabilityByLectureId = lectureRepository
			.findLectureAvailabilities(recruitmentEntity)
			.stream()
			.collect(Collectors.toMap(LectureRepository.LectureAvailability::getId, Function.identity()));
		Map<Long, Long> activeDraftCountByLectureId = participantLectureDraftRepository
			.countActiveDraftsByLecture(recruitmentEntity, now)
			.stream()
			.collect(Collectors.toMap(
				ParticipantLectureDraftRepository.LectureDraftCount::getLectureId,
				ParticipantLectureDraftRepository.LectureDraftCount::getDraftCount
			));

		return new LectureSelection(
			includeMorning ? toLectureViews(staticLectures, availabilityByLectureId, activeDraftCountByLectureId, LectureType.AM) : List.of(),
			includeAfternoon ? toLectureViews(staticLectures, availabilityByLectureId, activeDraftCountByLectureId, LectureType.PM) : List.of()
		);
	}

	public void evictLectureStaticCache(Long recruitmentId) {
		afterCommitExecutor.execute(() -> lectureStaticCacheByRecruitmentId.invalidate(recruitmentId));
	}

	@Transactional(readOnly = true)
	public void warmUpLectureStaticCache(RecruitmentEntity recruitmentEntity) {
		if (recruitmentEntity == null) {
			return;
		}

		findStaticLectures(recruitmentEntity);
	}

	private List<LectureStaticView> findStaticLectures(RecruitmentEntity recruitmentEntity) {
		return lectureStaticCacheByRecruitmentId.get(
			recruitmentEntity.getId(),
			recruitmentId -> new LectureStaticCache(
				lectureRepository.findByRecruitmentEntityOrderByTypeAscSortOrderAscIdAsc(recruitmentEntity)
					.stream()
					.map(LectureStaticView::from)
					.toList()
			)
		).lectures();
	}

	private List<LectureView> toLectureViews(
		List<LectureStaticView> staticLectures,
		Map<Long, LectureRepository.LectureAvailability> availabilityByLectureId,
		Map<Long, Long> activeDraftCountByLectureId,
		LectureType type
	) {
		return staticLectures.stream()
			.filter(lecture -> lecture.type() == type)
			.map(lecture -> LectureView.from(
				lecture,
				availabilityByLectureId.get(lecture.id()),
				activeDraftCountByLectureId.getOrDefault(lecture.id(), 0L)
			))
			.sorted(this::compareOpenFirst)
			.toList();
	}

	private int compareOpenFirst(LectureView first, LectureView second) {
		if (first.open() != second.open()) {
			return first.open() ? -1 : 1;
		}

		return 0;
	}

	private record LectureStaticCache(List<LectureStaticView> lectures) {
	}

	private record LectureStaticView(
		Long id,
		String speakerName,
		String speakerJob,
		String description,
		LectureType type,
		Integer sortOrder
	) {
		private static LectureStaticView from(LectureEntity lectureEntity) {
			return new LectureStaticView(
				lectureEntity.getId(),
				lectureEntity.getSpeakerName(),
				lectureEntity.getSpeakerJob(),
				lectureEntity.getDescription(),
				lectureEntity.getType(),
				lectureEntity.getSortOrder()
			);
		}
	}

	public record LectureView(
		Long id,
		String speakerName,
		String speakerJob,
		String description,
		LectureType type,
		Integer sortOrder,
		boolean open,
		Integer maxCapacity,
		Integer participantCount,
		Long draftCount
	) {
		private static LectureView from(
			LectureStaticView lecture,
			LectureRepository.LectureAvailability availability,
			Long draftCount
		) {
			if (availability == null) {
				throw new IllegalStateException("강의 상태 정보를 찾을 수 없습니다.");
			}

			return new LectureView(
				lecture.id(),
				lecture.speakerName(),
				lecture.speakerJob(),
				lecture.description(),
				lecture.type(),
				lecture.sortOrder(),
				Boolean.TRUE.equals(availability.getOpen()),
				availability.getMaxCapacity(),
				availability.getParticipantCount(),
				draftCount
			);
		}

		public int getRemainingCapacity() {
			return maxCapacity - participantCount - draftCount.intValue();
		}

		public int getUserVisibleRemainingCapacity() {
			return Math.max(0, getRemainingCapacity());
		}

		public boolean isFull() {
			return getRemainingCapacity() <= 0;
		}

		public boolean isOpen() {
			return open;
		}
	}

	public record LectureSelection(
		List<LectureView> morningLectures,
		List<LectureView> afternoonLectures
	) {
		private List<LectureView> lecturesByType(LectureType type) {
			if (type == LectureType.AM) {
				return morningLectures;
			}

			return afternoonLectures;
		}
	}
}
