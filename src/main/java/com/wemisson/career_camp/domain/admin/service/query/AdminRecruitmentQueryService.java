package com.wemisson.career_camp.domain.admin.service.query;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wemisson.career_camp.domain.participant.entity.ParticipantEntity;
import com.wemisson.career_camp.domain.participant.entity.ParticipantLectureEntity;
import com.wemisson.career_camp.domain.participant.repository.ParticipantLectureRepository;
import com.wemisson.career_camp.domain.participant.repository.ParticipantRepository;
import com.wemisson.career_camp.domain.participant.repository.ParticipantTypeRepository;
import com.wemisson.career_camp.domain.recruitment.dto.LectureType;
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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminRecruitmentQueryService {

	private final RecruitmentRepository recruitmentRepository;
	private final ParticipantRepository participantRepository;
	private final ParticipantLectureRepository participantLectureRepository;
	private final ParticipantTypeRepository participantTypeRepository;
	private final LectureRepository lectureRepository;
	private final RecruitmentChurchRepository recruitmentChurchRepository;
	private final RecruitmentParticipantTypeRepository recruitmentParticipantTypeRepository;
	private final RecruitmentParticipantTypeFixedLectureRepository fixedLectureRepository;

	@Transactional(readOnly = true)
	public ParticipantsView findParticipantsView(
		Long recruitmentId,
		String reportType,
		LectureType lectureType,
		Long lectureId,
		Long churchId,
		int page,
		int pageSize
	) {
		RecruitmentEntity recruitmentEntity = findRecruitment(recruitmentId);
		String normalizedReportType = normalizeReportType(reportType);

		if ("lecture".equals(normalizedReportType)) {
			return findLectureParticipantsView(recruitmentEntity, lectureType, lectureId);
		}
		if ("church".equals(normalizedReportType)) {
			return findChurchParticipantsView(recruitmentEntity, churchId);
		}

		return findAllParticipantsView(recruitmentEntity, page, pageSize);
	}

	private ParticipantsView findAllParticipantsView(
		RecruitmentEntity recruitmentEntity,
		int page,
		int pageSize
	) {
		int normalizedPageSize = normalizePageSize(pageSize);
		int currentPage = Math.max(page, 0);
		Page<ParticipantEntity> allParticipantPage = participantRepository.findByRecruitmentEntityOrderByIdAsc(
			recruitmentEntity,
			PageRequest.of(currentPage, normalizedPageSize)
		);
		if (allParticipantPage.isEmpty()
			&& allParticipantPage.getTotalPages() > 0
			&& currentPage >= allParticipantPage.getTotalPages()) {
			currentPage = allParticipantPage.getTotalPages() - 1;
			allParticipantPage = participantRepository.findByRecruitmentEntityOrderByIdAsc(
				recruitmentEntity,
				PageRequest.of(currentPage, normalizedPageSize)
			);
		}

		List<ParticipantEntity> allParticipants = allParticipantPage.getContent();

		return new ParticipantsView(
			recruitmentEntity,
			"all",
			LectureType.AM,
			List.of(),
			List.of(),
			List.of(),
			allParticipants,
			allParticipantPage,
			currentPage,
			normalizedPageSize,
			buildPageNumbers(allParticipantPage.getTotalPages(), currentPage),
			null,
			null,
			List.of(),
			List.of(),
			findParticipantLecturesByParticipantId(allParticipants),
			Map.of(),
			Map.of(),
			buildAllParticipantsUrl(recruitmentEntity.getId(), currentPage, normalizedPageSize)
		);
	}

	private ParticipantsView findLectureParticipantsView(
		RecruitmentEntity recruitmentEntity,
		LectureType lectureType,
		Long lectureId
	) {
		LectureType selectedLectureType = lectureType == null ? LectureType.AM : lectureType;
		List<LectureEntity> selectedTypeLectures = lectureRepository
			.findByRecruitmentEntityAndTypeOrderBySortOrderAscIdAsc(recruitmentEntity, selectedLectureType);
		LectureEntity selectedLecture = selectLecture(recruitmentEntity, selectedTypeLectures, lectureId);
		List<ParticipantLectureEntity> selectedLectureApplications = selectedLecture == null
			? List.of()
			: findLectureApplications(selectedLecture);

		return new ParticipantsView(
			recruitmentEntity,
			"lecture",
			selectedLectureType,
			List.of(),
			selectedTypeLectures,
			List.of(),
			List.of(),
			Page.empty(),
			0,
			normalizePageSize(0),
			List.of(),
			selectedLecture,
			null,
			selectedLectureApplications,
			List.of(),
			Map.of(),
			getLectureCounts(selectedTypeLectures),
			Map.of(),
			buildLectureParticipantsUrl(recruitmentEntity.getId(), selectedLectureType, selectedLecture)
		);
	}

	private ParticipantsView findChurchParticipantsView(
		RecruitmentEntity recruitmentEntity,
		Long churchId
	) {
		List<RecruitmentChurchEntity> churches = recruitmentChurchRepository
			.findByRecruitmentEntityOrderBySortOrderAscIdAsc(recruitmentEntity);
		RecruitmentChurchEntity selectedChurch = selectChurch(recruitmentEntity, churches, churchId);
		List<ParticipantEntity> selectedChurchParticipants = selectedChurch == null
			? List.of()
			: participantRepository.findByRecruitmentChurchEntityOrderByNameAsc(selectedChurch);

		return new ParticipantsView(
			recruitmentEntity,
			"church",
			LectureType.AM,
			List.of(),
			List.of(),
			churches,
			List.of(),
			Page.empty(),
			0,
			normalizePageSize(0),
			List.of(),
			null,
			selectedChurch,
			List.of(),
			selectedChurchParticipants,
			findParticipantLecturesByParticipantId(selectedChurchParticipants),
			Map.of(),
			getChurchCounts(churches),
			buildChurchParticipantsUrl(recruitmentEntity.getId(), selectedChurch)
		);
	}

	@Transactional
	public SettingsView findSettingsView(Long recruitmentId) {
		RecruitmentEntity recruitmentEntity = findRecruitment(recruitmentId);
		List<RecruitmentParticipantTypeEntity> participantTypeRules = ensureFixedParticipantTypeRules(
			recruitmentEntity
		);

		return new SettingsView(
			recruitmentEntity,
			lectureRepository.findByRecruitmentEntityOrderByTypeAscSortOrderAscIdAsc(recruitmentEntity),
			recruitmentChurchRepository.findByRecruitmentEntityOrderBySortOrderAscIdAsc(recruitmentEntity),
			participantTypeRules,
			findFixedLectureSelections(recruitmentEntity, participantTypeRules)
		);
	}

	@Transactional(readOnly = true)
	public ExcelSource findExcelSource(
		Long recruitmentId,
		String downloadType
	) {
		RecruitmentEntity recruitmentEntity = findRecruitment(recruitmentId);
		List<ParticipantEntity> participants = participantRepository.findByRecruitmentEntityOrderByNameAsc(
			recruitmentEntity
		);
		Map<Long, ParticipantLectureEntity> lectureByParticipantId = participantLectureRepository
			.findByParticipantEntityIn(participants)
			.stream()
			.collect(Collectors.toMap(
				participantLecture -> participantLecture.getParticipantEntity().getId(),
				Function.identity()
			));

		List<LectureEntity> lectures = lectureRepository.findByRecruitmentEntityOrderByTypeAscSortOrderAscIdAsc(
			recruitmentEntity
		);

		return new ExcelSource(
			recruitmentEntity,
			downloadType,
			participants,
			lectureByParticipantId,
			lectures,
			recruitmentChurchRepository.findByRecruitmentEntityOrderBySortOrderAscIdAsc(recruitmentEntity),
			findApplicationsByLectureId(lectures)
		);
	}

	private Map<Long, List<ParticipantLectureEntity>> findApplicationsByLectureId(List<LectureEntity> lectures) {
		if (lectures.isEmpty()) {
			return Map.of();
		}

		RecruitmentEntity recruitmentEntity = lectures.get(0).getRecruitmentEntity();
		Map<Long, List<ParticipantLectureEntity>> applicationsByLectureId = lectures.stream()
			.collect(Collectors.toMap(
				LectureEntity::getId,
				lecture -> new ArrayList<>()
			));

		participantLectureRepository.findApplicationsByRecruitmentEntity(recruitmentEntity)
			.forEach(application -> addApplicationByLectureId(applicationsByLectureId, application));

		return applicationsByLectureId;
	}

	private void addApplicationByLectureId(
		Map<Long, List<ParticipantLectureEntity>> applicationsByLectureId,
		ParticipantLectureEntity application
	) {
		if (application.getMorningLectureEntity() != null) {
			applicationsByLectureId
				.computeIfAbsent(application.getMorningLectureEntity().getId(), lectureId -> new ArrayList<>())
				.add(application);
		}
		if (application.getAfternoonLectureEntity() != null) {
			applicationsByLectureId
				.computeIfAbsent(application.getAfternoonLectureEntity().getId(), lectureId -> new ArrayList<>())
				.add(application);
		}
	}

	private RecruitmentEntity findRecruitment(Long recruitmentId) {
		return recruitmentRepository.findById(recruitmentId)
			.orElseThrow(() -> new IllegalArgumentException("모집을 찾을 수 없습니다."));
	}

	private RecruitmentChurchEntity findChurch(
		RecruitmentEntity recruitmentEntity,
		Long churchId
	) {
		return recruitmentChurchRepository.findByIdAndRecruitmentEntity(churchId, recruitmentEntity)
			.orElseThrow(() -> new IllegalArgumentException("교회를 찾을 수 없습니다."));
	}

	private List<RecruitmentParticipantTypeEntity> ensureFixedParticipantTypeRules(RecruitmentEntity recruitmentEntity) {
		List<RecruitmentParticipantTypeEntity> existingRules = recruitmentParticipantTypeRepository
			.findByRecruitmentEntityOrderByIdAsc(recruitmentEntity);
		Set<Long> existingParticipantTypeIds = existingRules.stream()
			.map(rule -> rule.getParticipantTypeEntity().getId())
			.collect(Collectors.toSet());

		participantTypeRepository.findAll()
			.stream()
			.filter(participantType -> !existingParticipantTypeIds.contains(participantType.getId()))
			.map(participantType -> RecruitmentParticipantTypeEntity.create(
				recruitmentEntity,
				participantType,
				false,
				false
			))
			.forEach(recruitmentParticipantTypeRepository::save);

		return recruitmentParticipantTypeRepository.findByRecruitmentEntityOrderByIdAsc(recruitmentEntity)
			.stream()
			.sorted(Comparator.comparing(rule -> rule.getParticipantTypeEntity().isStudent() ? 0 : 1))
			.toList();
	}

	private Map<Long, FixedLectureSelection> findFixedLectureSelections(
		RecruitmentEntity recruitmentEntity,
		List<RecruitmentParticipantTypeEntity> participantTypeRules
	) {
		Map<Long, FixedLectureSelection> selectionsByRuleId = new HashMap<>();
		participantTypeRules.forEach(rule -> selectionsByRuleId.put(rule.getId(), FixedLectureSelection.empty()));

		fixedLectureRepository.findByRecruitmentEntityWithRelations(recruitmentEntity)
			.forEach(fixedLecture -> mergeFixedLecture(selectionsByRuleId, fixedLecture));

		return Map.copyOf(selectionsByRuleId);
	}

	private void mergeFixedLecture(
		Map<Long, FixedLectureSelection> selectionsByRuleId,
		RecruitmentParticipantTypeFixedLectureEntity fixedLecture
	) {
		Long ruleId = fixedLecture.getRecruitmentParticipantTypeEntity().getId();
		FixedLectureSelection currentSelection = selectionsByRuleId.getOrDefault(
			ruleId,
			FixedLectureSelection.empty()
		);
		Long lectureId = fixedLecture.getLectureEntity().getId();

		selectionsByRuleId.put(
			ruleId,
			fixedLecture.getLectureType() == LectureType.AM
				? currentSelection.withMorningLecture(lectureId)
				: currentSelection.withAfternoonLecture(lectureId)
		);
	}

	private LectureEntity selectLecture(
		RecruitmentEntity recruitmentEntity,
		List<LectureEntity> lectures,
		Long lectureId
	) {
		if (lectures.isEmpty()) {
			return null;
		}
		if (lectureId == null) {
			return lectures.get(0);
		}

		LectureEntity lectureEntity = lectureRepository.findById(lectureId)
			.orElseThrow(() -> new IllegalArgumentException("강의를 찾을 수 없습니다."));

		if (!lectureEntity.getRecruitmentEntity().getId().equals(recruitmentEntity.getId())) {
			throw new IllegalArgumentException("해당 모집의 강의가 아닙니다.");
		}

		return lectureEntity;
	}

	private RecruitmentChurchEntity selectChurch(
		RecruitmentEntity recruitmentEntity,
		List<RecruitmentChurchEntity> churches,
		Long churchId
	) {
		if (churches.isEmpty()) {
			return null;
		}
		if (churchId == null) {
			return churches.get(0);
		}

		return findChurch(recruitmentEntity, churchId);
	}

	private List<ParticipantLectureEntity> findLectureApplications(LectureEntity lectureEntity) {
		if (lectureEntity.getType() == LectureType.AM) {
			return participantLectureRepository.findMorningApplicationsByLectureEntity(lectureEntity);
		}

		return participantLectureRepository.findAfternoonApplicationsByLectureEntity(lectureEntity);
	}

	private Map<Long, Integer> getLectureCounts(List<LectureEntity> lectures) {
		return lectures.stream()
			.collect(Collectors.toMap(LectureEntity::getId, LectureEntity::getParticipantCount));
	}

	private Map<Long, Integer> getChurchCounts(List<RecruitmentChurchEntity> churches) {
		if (churches.isEmpty()) {
			return Map.of();
		}

		Map<Long, Integer> countedChurches = participantRepository.countByRecruitmentChurchEntities(churches)
			.stream()
			.collect(Collectors.toMap(
				ParticipantRepository.ChurchParticipantCount::getChurchId,
				count -> count.getParticipantCount().intValue()
			));

		return churches.stream()
			.collect(Collectors.toMap(
				RecruitmentChurchEntity::getId,
				church -> countedChurches.getOrDefault(church.getId(), 0)
			));
	}

	private List<Integer> buildPageNumbers(
		int totalPages,
		int currentPage
	) {
		if (totalPages <= 0) {
			return List.of();
		}

		int start = Math.max(0, currentPage - 2);
		int end = Math.min(totalPages - 1, currentPage + 2);
		if (end - start < 4) {
			if (start == 0) {
				end = Math.min(totalPages - 1, start + 4);
			} else if (end == totalPages - 1) {
				start = Math.max(0, end - 4);
			}
		}

		List<Integer> pageNumbers = new ArrayList<>();
		for (int pageNumber = start; pageNumber <= end; pageNumber++) {
			pageNumbers.add(pageNumber);
		}

		return pageNumbers;
	}

	private Map<Long, ParticipantLectureEntity> findParticipantLecturesByParticipantId(
		List<ParticipantEntity> participants
	) {
		if (participants.isEmpty()) {
			return Map.of();
		}

		return participantLectureRepository.findByParticipantEntityIn(participants)
			.stream()
			.collect(Collectors.toMap(
				participantLecture -> participantLecture.getParticipantEntity().getId(),
				Function.identity()
			));
	}

	private String normalizeReportType(String reportType) {
		if ("lecture".equals(reportType) || "church".equals(reportType)) {
			return reportType;
		}

		return "all";
	}

	private String buildAllParticipantsUrl(
		Long recruitmentId,
		int page,
		int pageSize
	) {
		return new StringBuilder("/admin/recruitments/")
			.append(recruitmentId)
			.append("/participants?reportType=all")
			.append("&page=")
			.append(Math.max(page, 0))
			.append("&pageSize=")
			.append(normalizePageSize(pageSize))
			.toString();
	}

	private String buildLectureParticipantsUrl(
		Long recruitmentId,
		LectureType lectureType,
		LectureEntity selectedLecture
	) {
		StringBuilder url = new StringBuilder("/admin/recruitments/")
			.append(recruitmentId)
			.append("/participants?reportType=lecture")
			.append("&lectureType=")
			.append(lectureType.name());

		if (selectedLecture != null) {
			url.append("&lectureId=").append(selectedLecture.getId());
		}

		return url.toString();
	}

	private String buildChurchParticipantsUrl(
		Long recruitmentId,
		RecruitmentChurchEntity selectedChurch
	) {
		StringBuilder url = new StringBuilder("/admin/recruitments/")
			.append(recruitmentId)
			.append("/participants?reportType=church");

		if (selectedChurch != null) {
			url.append("&churchId=").append(selectedChurch.getId());
		}

		return url.toString();
	}

	private int normalizePageSize(int pageSize) {
		if (pageSize < 10) {
			return 10;
		}
		if (pageSize > 100) {
			return 100;
		}

		return pageSize;
	}

	public record ParticipantsView(
		RecruitmentEntity recruitment,
		String reportType,
		LectureType lectureType,
		List<LectureEntity> lectures,
		List<LectureEntity> filteredLectures,
		List<RecruitmentChurchEntity> churches,
		List<ParticipantEntity> allParticipants,
		Page<ParticipantEntity> allParticipantPage,
		int currentPage,
		int pageSize,
		List<Integer> pageNumbers,
		LectureEntity selectedLecture,
		RecruitmentChurchEntity selectedChurch,
		List<ParticipantLectureEntity> selectedLectureApplications,
		List<ParticipantEntity> selectedChurchParticipants,
		Map<Long, ParticipantLectureEntity> churchParticipantLectures,
		Map<Long, Integer> lectureCounts,
		Map<Long, Integer> churchCounts,
		String currentParticipantsUrl
	) {
	}

	public record SettingsView(
		RecruitmentEntity recruitment,
		List<LectureEntity> lectures,
		List<RecruitmentChurchEntity> churches,
		List<RecruitmentParticipantTypeEntity> participantTypeRules,
		Map<Long, FixedLectureSelection> fixedLecturesByRuleId
	) {
	}

	public record FixedLectureSelection(
		Long morningLectureId,
		Long afternoonLectureId
	) {
		private static FixedLectureSelection empty() {
			return new FixedLectureSelection(null, null);
		}

		private FixedLectureSelection withMorningLecture(Long lectureId) {
			return new FixedLectureSelection(lectureId, afternoonLectureId);
		}

		private FixedLectureSelection withAfternoonLecture(Long lectureId) {
			return new FixedLectureSelection(morningLectureId, lectureId);
		}
	}

	public record ExcelSource(
		RecruitmentEntity recruitment,
		String downloadType,
		List<ParticipantEntity> participants,
		Map<Long, ParticipantLectureEntity> lectureByParticipantId,
		List<LectureEntity> lectures,
		List<RecruitmentChurchEntity> churches,
		Map<Long, List<ParticipantLectureEntity>> applicationsByLectureId
	) {
	}
}
