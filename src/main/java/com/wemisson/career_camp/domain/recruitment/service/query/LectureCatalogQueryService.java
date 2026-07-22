package com.wemisson.career_camp.domain.recruitment.service.query;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wemisson.career_camp.common.transaction.AfterCommitExecutor;
import com.wemisson.career_camp.domain.participant.dto.ParticipantType;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentParticipantTypeFixedLectureEntity;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentParticipantTypeFixedLectureRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LectureCatalogQueryService {

	private final RecruitmentQueryService recruitmentQueryService;
	private final LectureQueryService lectureQueryService;
	private final RecruitmentParticipantTypeFixedLectureRepository fixedLectureRepository;
	private final AfterCommitExecutor afterCommitExecutor;
	private final Cache<Long, List<FixedLectureAssignment>> fixedLecturesByRecruitmentId = Caffeine.newBuilder()
		.maximumSize(128)
		.build();

	@Transactional(readOnly = true)
	public LectureCatalogView findLectureCatalog(
		RecruitmentEntity recruitmentEntity,
		Long participantTypeId
	) {
		List<RecruitmentQueryService.ParticipantTypeRule> rules = recruitmentQueryService
			.findParticipantTypeRules(recruitmentEntity);
		RecruitmentQueryService.ParticipantTypeRule selectedRule = rules.stream()
			.filter(rule -> rule.id().equals(participantTypeId))
			.findFirst()
			.orElseGet(() -> findDefaultParticipantTypeRule(rules));
		boolean showMorning = selectedRule == null
			|| selectedRule.usesFixedLectures()
			|| selectedRule.canSelectMorningLecture();
		boolean showAfternoon = selectedRule == null
			|| selectedRule.usesFixedLectures()
			|| selectedRule.canSelectAfternoonLecture();
		LectureQueryService.LectureSelection lectureSelection = lectureQueryService.findLectures(
			recruitmentEntity,
			showMorning,
			showAfternoon
		);

		if (selectedRule != null && selectedRule.usesFixedLectures()) {
			lectureSelection = filterFixedLectures(recruitmentEntity, selectedRule.id(), lectureSelection);
		}

		return new LectureCatalogView(
			rules.stream()
				.sorted(Comparator.comparingInt(rule -> rule.type().ordinal()))
				.map(rule -> new ParticipantTypeOption(
					rule.id(),
					rule.type().getDescription(),
					rule.usesFixedLectures()
				))
				.toList(),
			selectedRule == null ? null : selectedRule.id(),
			selectedRule != null && selectedRule.usesFixedLectures(),
			showMorning,
			showAfternoon,
			lectureSelection
		);
	}

	private RecruitmentQueryService.ParticipantTypeRule findDefaultParticipantTypeRule(
		List<RecruitmentQueryService.ParticipantTypeRule> rules
	) {
		return rules.stream()
			.filter(rule -> rule.type() == ParticipantType.STUDENT)
			.findFirst()
			.orElseGet(() -> rules.isEmpty() ? null : rules.getFirst());
	}

	public void evictFixedLectureCache(Long recruitmentId) {
		afterCommitExecutor.execute(() -> fixedLecturesByRecruitmentId.invalidate(recruitmentId));
	}

	private LectureQueryService.LectureSelection filterFixedLectures(
		RecruitmentEntity recruitmentEntity,
		Long participantTypeId,
		LectureQueryService.LectureSelection lectureSelection
	) {
		Set<Long> fixedLectureIds = findFixedLectures(recruitmentEntity).stream()
			.filter(fixedLecture -> fixedLecture.participantTypeId().equals(participantTypeId))
			.map(FixedLectureAssignment::lectureId)
			.collect(Collectors.toSet());

		return new LectureQueryService.LectureSelection(
			lectureSelection.morningLectures().stream()
				.filter(lecture -> fixedLectureIds.contains(lecture.id()))
				.toList(),
			lectureSelection.afternoonLectures().stream()
				.filter(lecture -> fixedLectureIds.contains(lecture.id()))
				.toList()
		);
	}

	private List<FixedLectureAssignment> findFixedLectures(RecruitmentEntity recruitmentEntity) {
		return fixedLecturesByRecruitmentId.get(
			recruitmentEntity.getId(),
			recruitmentId -> fixedLectureRepository.findByRecruitmentEntityWithRelations(recruitmentEntity)
				.stream()
				.map(FixedLectureAssignment::from)
				.toList()
		);
	}

	private record FixedLectureAssignment(
		Long participantTypeId,
		Long lectureId
	) {
		private static FixedLectureAssignment from(
			RecruitmentParticipantTypeFixedLectureEntity fixedLecture
		) {
			return new FixedLectureAssignment(
				fixedLecture.getRecruitmentParticipantTypeEntity().getParticipantTypeEntity().getId(),
				fixedLecture.getLectureEntity().getId()
			);
		}
	}

	public record ParticipantTypeOption(
		Long id,
		String description,
		boolean fixedLectures
	) {
	}

	public record LectureCatalogView(
		List<ParticipantTypeOption> participantTypes,
		Long selectedParticipantTypeId,
		boolean fixedLectures,
		boolean showMorning,
		boolean showAfternoon,
		LectureQueryService.LectureSelection lectureSelection
	) {
	}
}
