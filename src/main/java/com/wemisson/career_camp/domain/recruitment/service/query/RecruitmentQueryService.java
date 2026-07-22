package com.wemisson.career_camp.domain.recruitment.service.query;

import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wemisson.career_camp.common.transaction.AfterCommitExecutor;
import com.wemisson.career_camp.domain.participant.dto.ParticipantType;
import com.wemisson.career_camp.domain.recruitment.dto.RecruitmentStatus;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentChurchEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentParticipantTypeEntity;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentChurchRepository;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentParticipantTypeRepository;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RecruitmentQueryService {

	private static final String CURRENT_RECRUITMENT_CACHE_KEY = "current";
	private static final String ACTIVE_VISIBLE_RECRUITMENT_CACHE_KEY = "activeVisible";
	private static final String LATEST_CLOSED_RECRUITMENT_CACHE_KEY = "latestClosed";
	private static final String SINGLE_RECRUITMENT_SCOPED_CACHE_KEY = "value";
	private static final Duration RECRUITMENT_VISIBILITY_CACHE_TTL = Duration.ofMinutes(1);

	private final RecruitmentRepository recruitmentRepository;
	private final RecruitmentParticipantTypeRepository recruitmentParticipantTypeRepository;
	private final RecruitmentChurchRepository recruitmentChurchRepository;
	private final AfterCommitExecutor afterCommitExecutor;
	private final Cache<String, Optional<RecruitmentEntity>> currentRecruitmentCache = Caffeine.newBuilder()
		.expireAfterWrite(RECRUITMENT_VISIBILITY_CACHE_TTL)
		.maximumSize(1)
		.build();
	private final Cache<String, Optional<RecruitmentEntity>> activeVisibleRecruitmentCache = Caffeine.newBuilder()
		.expireAfterWrite(RECRUITMENT_VISIBILITY_CACHE_TTL)
		.maximumSize(1)
		.build();
	private final Cache<String, Optional<RecruitmentEntity>> latestClosedRecruitmentCache = Caffeine.newBuilder()
		.expireAfterWrite(RECRUITMENT_VISIBILITY_CACHE_TTL)
		.maximumSize(1)
		.build();
	private final Cache<String, RecruitmentCacheEntry<List<ParticipantTypeRule>>> participantTypeRulesCache = Caffeine.newBuilder()
		.maximumSize(1)
		.build();
	private final Cache<String, RecruitmentCacheEntry<List<RecruitmentChurchView>>> churchesCache = Caffeine.newBuilder()
		.maximumSize(1)
		.build();

	public Optional<RecruitmentEntity> findCurrentRecruitment() {
		return currentRecruitmentCache.get(
			CURRENT_RECRUITMENT_CACHE_KEY,
			key -> recruitmentRepository
				.findByStatusOrderByIdDesc(RecruitmentStatus.OPEN)
				.stream()
				.findFirst()
		);
	}

	@Transactional(readOnly = true)
	public RecruitmentEntity findRecruitment(Long recruitmentId) {
		return recruitmentRepository.findById(recruitmentId)
			.orElseThrow(() -> new IllegalArgumentException("모집을 찾을 수 없습니다."));
	}

	public void evictCurrentRecruitmentCache() {
		afterCommitExecutor.execute(() -> {
			currentRecruitmentCache.invalidate(CURRENT_RECRUITMENT_CACHE_KEY);
			activeVisibleRecruitmentCache.invalidate(ACTIVE_VISIBLE_RECRUITMENT_CACHE_KEY);
			latestClosedRecruitmentCache.invalidate(LATEST_CLOSED_RECRUITMENT_CACHE_KEY);
		});
	}

	public void warmUpRecruitmentCaches(RecruitmentEntity recruitmentEntity) {
		if (recruitmentEntity == null) {
			return;
		}

		Optional<RecruitmentEntity> recruitment = Optional.of(recruitmentEntity);

		if (recruitmentEntity.isOpen()) {
			currentRecruitmentCache.put(CURRENT_RECRUITMENT_CACHE_KEY, recruitment);
			activeVisibleRecruitmentCache.put(ACTIVE_VISIBLE_RECRUITMENT_CACHE_KEY, recruitment);
		} else if (recruitmentEntity.isWaiting()) {
			activeVisibleRecruitmentCache.put(ACTIVE_VISIBLE_RECRUITMENT_CACHE_KEY, recruitment);
		}

		findChurches(recruitmentEntity);
		findParticipantTypeRules(recruitmentEntity);
	}

	public Optional<RecruitmentEntity> findVisibleRecruitment() {
		return findActiveVisibleRecruitment()
			.or(this::findLatestClosedRecruitment);
	}

	private Optional<RecruitmentEntity> findActiveVisibleRecruitment() {
		return activeVisibleRecruitmentCache.get(
			ACTIVE_VISIBLE_RECRUITMENT_CACHE_KEY,
			key -> recruitmentRepository
				.findByStatusIn(List.of(RecruitmentStatus.OPEN, RecruitmentStatus.WAITING))
				.stream()
				.sorted(
					Comparator
						.comparing(RecruitmentEntity::isOpen)
						.reversed()
						.thenComparing(RecruitmentEntity::getId, Comparator.reverseOrder())
				)
				.findFirst()
		);
	}

	private Optional<RecruitmentEntity> findLatestClosedRecruitment() {
		return latestClosedRecruitmentCache.get(
			LATEST_CLOSED_RECRUITMENT_CACHE_KEY,
			key -> recruitmentRepository
				.findByStatusOrderByIdDesc(RecruitmentStatus.CLOSED)
				.stream()
				.findFirst()
		);
	}

	public List<ParticipantTypeRule> findParticipantTypeRules(RecruitmentEntity recruitmentEntity) {
		return getRecruitmentScopedCacheValue(
			participantTypeRulesCache,
			recruitmentEntity,
			() -> recruitmentParticipantTypeRepository.findByRecruitmentEntityOrderByIdAsc(recruitmentEntity)
				.stream()
				.map(ParticipantTypeRule::from)
				.toList()
		);
	}

	public ParticipantTypeRule findParticipantTypeRule(
		RecruitmentEntity recruitmentEntity,
		Long participantTypeId
	) {
		return findParticipantTypeRules(recruitmentEntity)
			.stream()
			.filter(rule -> rule.id().equals(participantTypeId))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("현재 모집에서 신청할 수 없는 참가자 유형입니다."));
	}

	public List<RecruitmentChurchView> findChurches(RecruitmentEntity recruitmentEntity) {
		Objects.requireNonNull(recruitmentEntity, "recruitmentEntity must not be null");

		return getRecruitmentScopedCacheValue(
			churchesCache,
			recruitmentEntity,
			() -> recruitmentChurchRepository.findByRecruitmentEntityOrderBySortOrderAscIdAsc(recruitmentEntity)
				.stream()
				.map(RecruitmentChurchView::from)
				.toList()
		);
	}

	public boolean isSelectableChurch(RecruitmentEntity recruitmentEntity, Long churchId) {
		return findChurches(recruitmentEntity)
			.stream()
			.anyMatch(church -> church.id().equals(churchId));
	}

	public List<String> findNoticeLines(RecruitmentEntity recruitmentEntity) {
		if (recruitmentEntity == null) {
			return List.of("현재 신청 가능한 모집이 없습니다.");
		}

		return Arrays.stream(normalizeNotice(recruitmentEntity.getNotice()).split("\n", -1))
			.map(String::stripLeading)
			.toList();
	}

	public void evictParticipantTypeRuleCache(Long recruitmentId) {
		afterCommitExecutor.execute(participantTypeRulesCache::invalidateAll);
	}

	public void evictChurchCache(Long recruitmentId) {
		afterCommitExecutor.execute(churchesCache::invalidateAll);
	}

	public void evictRecruitmentCaches(Long recruitmentId) {
		evictCurrentRecruitmentCache();
		evictParticipantTypeRuleCache(recruitmentId);
		evictChurchCache(recruitmentId);
	}

	private String normalizeNotice(String notice) {
		if (notice == null) {
			return "";
		}

		return notice
			.replace("\\r\\n", "\n")
			.replace("\\n", "\n")
			.replace("\r\n", "\n")
			.replace("\r", "\n");
	}

	private <T> T getRecruitmentScopedCacheValue(
		Cache<String, RecruitmentCacheEntry<T>> cache,
		RecruitmentEntity recruitmentEntity,
		java.util.function.Supplier<T> loader
	) {
		return cache.asMap().compute(SINGLE_RECRUITMENT_SCOPED_CACHE_KEY, (key, cachedEntry) -> {
			if (cachedEntry != null && cachedEntry.recruitmentId().equals(recruitmentEntity.getId())) {
				return cachedEntry;
			}

			return new RecruitmentCacheEntry<>(recruitmentEntity.getId(), loader.get());
		}).value();
	}

	private record RecruitmentCacheEntry<T>(
		Long recruitmentId,
		T value
	) {
	}

	public record ParticipantTypeRule(
		Long id,
		ParticipantType type,
		boolean canSelectMorningLecture,
		boolean canSelectAfternoonLecture
	) {
		public boolean usesFixedLectures() {
			return !canSelectMorningLecture && !canSelectAfternoonLecture;
		}

		private static ParticipantTypeRule from(RecruitmentParticipantTypeEntity rule) {
			return new ParticipantTypeRule(
				rule.getParticipantTypeEntity().getId(),
				rule.getParticipantTypeEntity().getType(),
				rule.canSelectMorningLecture(),
				rule.canSelectAfternoonLecture()
			);
		}
	}

	public record RecruitmentChurchView(
		Long id,
		String name,
		Integer sortOrder
	) {
		private static RecruitmentChurchView from(RecruitmentChurchEntity churchEntity) {
			return new RecruitmentChurchView(
				churchEntity.getId(),
				churchEntity.getName(),
				churchEntity.getSortOrder()
			);
		}
	}
}
