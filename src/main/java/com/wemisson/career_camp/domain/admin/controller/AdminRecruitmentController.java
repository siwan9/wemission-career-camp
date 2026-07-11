package com.wemisson.career_camp.domain.admin.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.wemisson.career_camp.domain.admin.dto.AdminParticipantResult;
import com.wemisson.career_camp.domain.participant.entity.ParticipantEntity;
import com.wemisson.career_camp.domain.participant.entity.ParticipantLectureEntity;
import com.wemisson.career_camp.domain.participant.entity.ParticipantTypeEntity;
import com.wemisson.career_camp.domain.participant.repository.ParticipantLectureRepository;
import com.wemisson.career_camp.domain.participant.repository.ParticipantRepository;
import com.wemisson.career_camp.domain.participant.repository.ParticipantTypeRepository;
import com.wemisson.career_camp.domain.participant.service.ParticipantLookupService;
import com.wemisson.career_camp.domain.recruitment.entity.LectureEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentChurchEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentParticipantTypeEntity;
import com.wemisson.career_camp.domain.recruitment.dto.LectureType;
import com.wemisson.career_camp.domain.recruitment.repository.LectureRepository;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentChurchRepository;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentParticipantTypeRepository;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentRepository;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class AdminRecruitmentController {

	public static final String ADMIN_RETURN_URL_SESSION_KEY = "adminReturnUrl";
	private static final String REGISTRATION_REQUEST_SESSION_KEY = "registrationRequest";
	private static final String PARTICIPANT_LECTURE_ID_SESSION_KEY = "participantLectureId";
	private static final String EDITING_PARTICIPANT_ID_SESSION_KEY = "editingParticipantId";

	private final RecruitmentRepository recruitmentRepository;
	private final ParticipantRepository participantRepository;
	private final ParticipantLectureRepository participantLectureRepository;
	private final ParticipantTypeRepository participantTypeRepository;
	private final LectureRepository lectureRepository;
	private final RecruitmentChurchRepository recruitmentChurchRepository;
	private final RecruitmentParticipantTypeRepository recruitmentParticipantTypeRepository;
	private final ParticipantLookupService participantLookupService;

	@GetMapping("/admin/recruitments/{recruitmentId}/participants")
	public String participants(
		@PathVariable Long recruitmentId,
		@RequestParam(required = false, defaultValue = "lecture") String reportType,
		@RequestParam(required = false, defaultValue = "AM") LectureType lectureType,
		@RequestParam(required = false) Long lectureId,
		@RequestParam(required = false) Long churchId,
		@RequestParam(required = false, defaultValue = "0") int page,
		@RequestParam(required = false, defaultValue = "50") int pageSize,
		Model model
	) {
		RecruitmentEntity recruitmentEntity = findRecruitment(recruitmentId);
		List<LectureEntity> lectures = lectureRepository
			.findByRecruitmentEntityOrderByTypeAscSortOrderAscIdAsc(recruitmentEntity);
		List<LectureEntity> filteredLectures = lectures.stream()
			.filter(lecture -> lecture.getType() == lectureType)
			.toList();
		List<RecruitmentChurchEntity> churches = recruitmentChurchRepository
			.findByRecruitmentEntityOrderBySortOrderAscIdAsc(recruitmentEntity);
		boolean churchReport = "church".equals(reportType);
		boolean allReport = "all".equals(reportType);
		LectureEntity selectedLecture = null;
		RecruitmentChurchEntity selectedChurch = null;
		List<ParticipantEntity> allParticipants = List.of();
		Page<ParticipantEntity> allParticipantPage = Page.empty();
		int currentAllParticipantPage = Math.max(page, 0);
		List<ParticipantLectureEntity> selectedLectureApplications = List.of();
		List<ParticipantEntity> selectedChurchParticipants = List.of();

		if (allReport) {
			int normalizedPageSize = normalizePageSize(pageSize);
			allParticipantPage = participantRepository.findByRecruitmentEntityOrderByIdAsc(
				recruitmentEntity,
				PageRequest.of(currentAllParticipantPage, normalizedPageSize)
			);
			if (allParticipantPage.isEmpty() && allParticipantPage.getTotalPages() > 0 && currentAllParticipantPage >= allParticipantPage.getTotalPages()) {
				currentAllParticipantPage = allParticipantPage.getTotalPages() - 1;
				allParticipantPage = participantRepository.findByRecruitmentEntityOrderByIdAsc(
					recruitmentEntity,
					PageRequest.of(currentAllParticipantPage, normalizedPageSize)
				);
			}
			allParticipants = allParticipantPage.getContent();
			model.addAttribute("allParticipantPage", allParticipantPage);
			model.addAttribute("currentPage", currentAllParticipantPage);
			model.addAttribute("pageSize", normalizedPageSize);
			model.addAttribute("pageNumbers", buildPageNumbers(allParticipantPage.getTotalPages(), currentAllParticipantPage));
		} else if (churchReport) {
			selectedChurch = selectChurch(recruitmentEntity, churches, churchId);
			if (selectedChurch != null) {
				selectedChurchParticipants = participantRepository.findByRecruitmentChurchEntityOrderByNameAsc(
					selectedChurch
				);
			}
		} else {
			selectedLecture = selectLecture(recruitmentEntity, filteredLectures, lectureId);
			if (selectedLecture != null) {
				selectedLectureApplications = findLectureApplications(selectedLecture);
			}
		}
		Map<Long, ParticipantLectureEntity> churchParticipantLectures = participantLectureRepository
			.findByParticipantEntityIn(allReport ? allParticipants : selectedChurchParticipants)
			.stream()
			.collect(Collectors.toMap(
				participantLecture -> participantLecture.getParticipantEntity().getId(),
				Function.identity()
			));

		model.addAttribute("recruitment", recruitmentEntity);
		model.addAttribute("reportType", allReport ? "all" : (churchReport ? "church" : "lecture"));
		model.addAttribute("lectureType", lectureType);
		model.addAttribute("lectures", lectures);
		model.addAttribute("filteredLectures", filteredLectures);
		model.addAttribute("churches", churches);
		model.addAttribute("allParticipants", allParticipants);
		model.addAttribute("selectedLecture", selectedLecture);
		model.addAttribute("selectedChurch", selectedChurch);
		model.addAttribute("selectedLectureApplications", selectedLectureApplications);
		model.addAttribute("selectedChurchParticipants", selectedChurchParticipants);
		model.addAttribute("churchParticipantLectures", churchParticipantLectures);
		model.addAttribute("lectureCounts", getLectureCounts(lectures));
		model.addAttribute("churchCounts", getChurchCounts(churches));
		model.addAttribute("currentParticipantsUrl", buildParticipantsUrl(
			recruitmentId,
			churchReport,
			allReport,
			lectureType,
			selectedLecture,
			selectedChurch,
			currentAllParticipantPage,
			pageSize
		));

		return "admin/participants";
	}

	@GetMapping("/admin/recruitments/{recruitmentId}/settings")
	@Transactional
	public String settings(
		@PathVariable Long recruitmentId,
		Model model
	) {
		RecruitmentEntity recruitmentEntity = findRecruitment(recruitmentId);
		List<RecruitmentParticipantTypeEntity> participantTypeRules = ensureFixedParticipantTypeRules(recruitmentEntity);

		model.addAttribute("recruitment", recruitmentEntity);
		model.addAttribute(
			"lectures",
			lectureRepository.findByRecruitmentEntityOrderByTypeAscSortOrderAscIdAsc(recruitmentEntity)
		);
		model.addAttribute("churches", recruitmentChurchRepository.findByRecruitmentEntityOrderBySortOrderAscIdAsc(recruitmentEntity));
		model.addAttribute("participantTypeRules", participantTypeRules);

		return "admin/settings";
	}

	@GetMapping("/admin/recruitments/{recruitmentId}/excel")
	@Transactional(readOnly = true)
	public ResponseEntity<byte[]> downloadExcel(
		@PathVariable Long recruitmentId,
		@RequestParam(required = false, defaultValue = "lecture") String downloadType
	) throws IOException {
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
		List<RecruitmentChurchEntity> churches = recruitmentChurchRepository.findByRecruitmentEntityOrderBySortOrderAscIdAsc(
			recruitmentEntity
		);
		byte[] excelBytes = createRecruitmentExcel(
			recruitmentEntity,
			downloadType,
			participants,
			lectureByParticipantId,
			lectures,
			churches
		);
		String reportName = "church".equals(downloadType) ? "교회별현황" : "강좌별현황";
		String filename = sanitizeFilename(recruitmentEntity.getName()) + "_" + reportName + ".xlsx";

		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
				.filename(filename, StandardCharsets.UTF_8)
				.build()
				.toString())
			.body(excelBytes);
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/participants/{participantId}/delete")
	@Transactional
	public String deleteParticipant(
		@PathVariable Long recruitmentId,
		@PathVariable Long participantId,
		@RequestParam(required = false) String returnUrl
	) {
		ParticipantEntity participantEntity = findParticipant(recruitmentId, participantId);
		participantLectureRepository.findByParticipantEntity(participantEntity)
			.ifPresent(participantLecture -> {
				decreaseIfPresent(participantLecture.getMorningLectureEntity());
				decreaseIfPresent(participantLecture.getAfternoonLectureEntity());
				participantLectureRepository.delete(participantLecture);
			});
		participantRepository.delete(participantEntity);

		return "redirect:" + getAdminReturnUrl(recruitmentId, returnUrl);
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/settings")
	@Transactional
	public String updateRecruitment(
		@PathVariable Long recruitmentId,
		@RequestParam String name,
		@RequestParam String description,
		@RequestParam String notice,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
		@RequestParam(required = false, defaultValue = "false") boolean isOpen,
		RedirectAttributes redirectAttributes
	) {
		RecruitmentEntity recruitmentEntity = findRecruitment(recruitmentId);
		if (isOpen && recruitmentRepository.existsByIsOpenTrueAndIdNot(recruitmentId)) {
			redirectAttributes.addFlashAttribute("toastMessage", "이미 모집중인 모집이 있어 모집중으로 변경할 수 없습니다.");
			return "redirect:/admin/recruitments/" + recruitmentId + "/settings";
		}
		recruitmentEntity.update(name, description, notice, startAt, endAt, isOpen);
		redirectAttributes.addFlashAttribute("toastMessage", "모집 정보가 저장되었습니다.");

		return "redirect:/admin/recruitments/" + recruitmentId + "/settings";
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/toggle-open")
	@Transactional
	public String toggleRecruitmentOpen(
		@PathVariable Long recruitmentId,
		RedirectAttributes redirectAttributes
	) {
		RecruitmentEntity recruitmentEntity = findRecruitment(recruitmentId);
		boolean nextOpen = !recruitmentEntity.isOpen();

		if (nextOpen && recruitmentRepository.existsByIsOpenTrueAndIdNot(recruitmentId)) {
			redirectAttributes.addFlashAttribute("errorMessage", "이미 모집중인 모집이 있어 모집중으로 변경할 수 없습니다.");

			return "redirect:/admin/recruitment-dashboard/" + recruitmentId;
		}

		recruitmentEntity.changeOpen(nextOpen);
		redirectAttributes.addFlashAttribute(
			"toastMessage",
			nextOpen ? "모집 상태가 모집중으로 변경되었습니다." : "모집 상태가 종료로 변경되었습니다."
		);

		return "redirect:/admin/recruitment-dashboard/" + recruitmentId;
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/participant-types/{ruleId}")
	@Transactional
	public String updateParticipantTypeRule(
		@PathVariable Long recruitmentId,
		@PathVariable Long ruleId,
		@RequestParam(required = false, defaultValue = "false") boolean canSelectMorningLecture,
		@RequestParam(required = false, defaultValue = "false") boolean canSelectAfternoonLecture,
		RedirectAttributes redirectAttributes
	) {
		RecruitmentEntity recruitmentEntity = findRecruitment(recruitmentId);
		RecruitmentParticipantTypeEntity rule = findParticipantTypeRule(recruitmentEntity, ruleId);

		rule.updateLecturePermission(canSelectMorningLecture, canSelectAfternoonLecture);
		redirectAttributes.addFlashAttribute("toastMessage", "참가자 타입 설정이 저장되었습니다.");

		return "redirect:/admin/recruitments/" + recruitmentId + "/settings";
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/lectures/{lectureId}")
	@Transactional
	public String updateLecture(
		@PathVariable Long recruitmentId,
		@PathVariable Long lectureId,
		@RequestParam String speakerName,
		@RequestParam String speakerJob,
		@RequestParam String description,
		@RequestParam LectureType type,
		@RequestParam(required = false, defaultValue = "false") boolean isOpen,
		@RequestParam Integer maxCapacity,
		RedirectAttributes redirectAttributes
	) {
		RecruitmentEntity recruitmentEntity = findRecruitment(recruitmentId);
		LectureEntity lectureEntity = lectureRepository.findById(lectureId)
			.orElseThrow(() -> new IllegalArgumentException("강의를 찾을 수 없습니다."));

		if (!lectureEntity.getRecruitmentEntity().getId().equals(recruitmentEntity.getId())) {
			throw new IllegalArgumentException("해당 모집의 강의가 아닙니다.");
		}
		validateMaxCapacity(maxCapacity);

		LectureType oldType = lectureEntity.getType();

		lectureEntity.update(speakerName, speakerJob, description, type, isOpen, maxCapacity);
		resequenceLectures(recruitmentEntity, oldType);
		resequenceLectures(recruitmentEntity, type);
		redirectAttributes.addFlashAttribute("toastMessage", "강의 정보가 저장되었습니다.");

		return "redirect:/admin/recruitments/" + recruitmentId + "/settings";
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/lectures")
	@Transactional
	public String createLecture(
		@PathVariable Long recruitmentId,
		@RequestParam String speakerName,
		@RequestParam String speakerJob,
		@RequestParam String description,
		@RequestParam LectureType type,
		@RequestParam(required = false, defaultValue = "false") boolean isOpen,
		@RequestParam Integer maxCapacity,
		RedirectAttributes redirectAttributes
	) {
		RecruitmentEntity recruitmentEntity = findRecruitment(recruitmentId);
		validateMaxCapacity(maxCapacity);
		int targetOrder = lectureRepository
			.findByRecruitmentEntityAndTypeOrderBySortOrderAscIdAsc(recruitmentEntity, type)
			.size() + 1;

		lectureRepository.save(
			LectureEntity.create(recruitmentEntity, speakerName, speakerJob, description, type, isOpen, maxCapacity, targetOrder)
		);
		resequenceLectures(recruitmentEntity, type);
		redirectAttributes.addFlashAttribute("toastMessage", "강의가 추가되었습니다.");

		return "redirect:/admin/recruitments/" + recruitmentId + "/settings";
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/lectures/{lectureId}/delete")
	@Transactional
	public String deleteLecture(
		@PathVariable Long recruitmentId,
		@PathVariable Long lectureId,
		RedirectAttributes redirectAttributes
	) {
		RecruitmentEntity recruitmentEntity = findRecruitment(recruitmentId);
		LectureEntity lectureEntity = lectureRepository.findById(lectureId)
			.orElseThrow(() -> new IllegalArgumentException("강의를 찾을 수 없습니다."));

		if (!lectureEntity.getRecruitmentEntity().getId().equals(recruitmentEntity.getId())) {
			throw new IllegalArgumentException("해당 모집의 강의가 아닙니다.");
		}
		if (lectureEntity.getParticipantCount() > 0) {
			throw new IllegalStateException("신청자가 있는 강의는 삭제할 수 없습니다.");
		}

		LectureType deletedType = lectureEntity.getType();
		lectureRepository.delete(lectureEntity);
		resequenceLectures(recruitmentEntity, deletedType);
		redirectAttributes.addFlashAttribute("toastMessage", "강의가 삭제되었습니다.");

		return "redirect:/admin/recruitments/" + recruitmentId + "/settings";
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/lectures/reorder")
	@Transactional
	@ResponseBody
	public String reorderLectures(
		@PathVariable Long recruitmentId,
		@RequestParam List<Long> lectureIds
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
		}

		return "ok";
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/churches")
	@Transactional
	public String createChurch(
		@PathVariable Long recruitmentId,
		@RequestParam String name,
		RedirectAttributes redirectAttributes
	) {
		RecruitmentEntity recruitmentEntity = findRecruitment(recruitmentId);
		List<RecruitmentChurchEntity> churches = recruitmentChurchRepository
			.findByRecruitmentEntityOrderBySortOrderAscIdAsc(recruitmentEntity);
		int targetOrder = churches.size() + 1;

		recruitmentChurchRepository.save(
			RecruitmentChurchEntity.create(recruitmentEntity, name, targetOrder)
		);
		resequenceChurches(recruitmentEntity);
		redirectAttributes.addFlashAttribute("toastMessage", "교회가 추가되었습니다.");

		return "redirect:/admin/recruitments/" + recruitmentId + "/settings";
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/churches/reorder")
	@Transactional
	@ResponseBody
	public String reorderChurches(
		@PathVariable Long recruitmentId,
		@RequestParam List<Long> churchIds
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

		return "ok";
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/churches/{churchId}")
	@Transactional
	public String updateChurch(
		@PathVariable Long recruitmentId,
		@PathVariable Long churchId,
		@RequestParam String name,
		@RequestParam Integer sortOrder,
		RedirectAttributes redirectAttributes
	) {
		RecruitmentEntity recruitmentEntity = findRecruitment(recruitmentId);
		RecruitmentChurchEntity churchEntity = findChurch(recruitmentEntity, churchId);
		churchEntity.update(name, sortOrder);
		resequenceChurches(recruitmentEntity);
		redirectAttributes.addFlashAttribute("toastMessage", "교회 정보가 저장되었습니다.");

		return "redirect:/admin/recruitments/" + recruitmentId + "/settings";
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/churches/{churchId}/delete")
	@Transactional
	public String deleteChurch(
		@PathVariable Long recruitmentId,
		@PathVariable Long churchId,
		RedirectAttributes redirectAttributes
	) {
		RecruitmentEntity recruitmentEntity = findRecruitment(recruitmentId);
		RecruitmentChurchEntity churchEntity = findChurch(recruitmentEntity, churchId);

		if (participantRepository.countByRecruitmentChurchEntity(churchEntity) > 0) {
			throw new IllegalStateException("신청자가 있는 교회는 삭제할 수 없습니다.");
		}

		recruitmentChurchRepository.delete(churchEntity);
		resequenceChurches(recruitmentEntity);
		redirectAttributes.addFlashAttribute("toastMessage", "교회가 삭제되었습니다.");

		return "redirect:/admin/recruitments/" + recruitmentId + "/settings";
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/participants/{participantId}/edit-info")
	public String editParticipantInfo(
		@PathVariable Long recruitmentId,
		@PathVariable Long participantId,
		HttpSession session,
		@RequestParam(required = false) String returnUrl
	) {
		ParticipantEntity participantEntity = findParticipant(recruitmentId, participantId);

		session.setAttribute(EDITING_PARTICIPANT_ID_SESSION_KEY, participantEntity.getId());
		session.setAttribute(ADMIN_RETURN_URL_SESSION_KEY, getAdminReturnUrl(recruitmentId, returnUrl));

		return "redirect:/register/edit";
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/participants/{participantId}/edit-lecture")
	public String editParticipantLecture(
		@PathVariable Long recruitmentId,
		@PathVariable Long participantId,
		HttpSession session,
		@RequestParam(required = false) String returnUrl
	) {
		ParticipantEntity participantEntity = findParticipant(recruitmentId, participantId);
		ParticipantLectureEntity participantLectureEntity = participantLectureRepository
			.findByParticipantEntity(participantEntity)
			.orElseGet(() -> participantLectureRepository.save(
				ParticipantLectureEntity.create(participantEntity)
			));

		session.setAttribute(
			REGISTRATION_REQUEST_SESSION_KEY,
			participantLookupService.toCreateRequest(participantLectureEntity)
		);
		session.setAttribute(PARTICIPANT_LECTURE_ID_SESSION_KEY, participantLectureEntity.getId());
		session.setAttribute(ADMIN_RETURN_URL_SESSION_KEY, getAdminReturnUrl(recruitmentId, returnUrl));

		return "redirect:/lecture";
	}

	public List<AdminParticipantResult> searchParticipants(
		RecruitmentEntity recruitmentEntity,
		String phoneNumber
	) {
		if (phoneNumber == null || phoneNumber.isBlank()) {
			return List.of();
		}

		List<ParticipantEntity> participants = participantRepository
			.findByRecruitmentEntityAndPhoneNumberContainingOrderByNameAsc(
				recruitmentEntity,
				phoneNumber.replaceAll("\\D", "")
			);
		Map<Long, ParticipantLectureEntity> lectureByParticipantId = participantLectureRepository
			.findByParticipantEntityIn(participants)
			.stream()
			.collect(Collectors.toMap(
				participantLecture -> participantLecture.getParticipantEntity().getId(),
				Function.identity()
			));

		return participants.stream()
			.map(participant -> AdminParticipantResult.of(
				participant,
				lectureByParticipantId.get(participant.getId())
			))
			.toList();
	}

	private RecruitmentEntity findRecruitment(Long recruitmentId) {
		return recruitmentRepository.findById(recruitmentId)
			.orElseThrow(() -> new IllegalArgumentException("모집을 찾을 수 없습니다."));
	}

	private ParticipantEntity findParticipant(Long recruitmentId, Long participantId) {
		RecruitmentEntity recruitmentEntity = findRecruitment(recruitmentId);
		ParticipantEntity participantEntity = participantRepository.findById(participantId)
			.orElseThrow(() -> new IllegalArgumentException("참가자를 찾을 수 없습니다."));

		if (!participantEntity.getRecruitmentEntity().getId().equals(recruitmentEntity.getId())) {
			throw new IllegalArgumentException("해당 모집의 참가자가 아닙니다.");
		}

		return participantEntity;
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
		RecruitmentParticipantTypeEntity rule = recruitmentParticipantTypeRepository.findById(ruleId)
			.orElseThrow(() -> new IllegalArgumentException("참가자 타입 설정을 찾을 수 없습니다."));

		if (!rule.getRecruitmentEntity().getId().equals(recruitmentEntity.getId())) {
			throw new IllegalArgumentException("해당 모집의 참가자 타입 설정이 아닙니다.");
		}

		return rule;
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
				participantType.isStudent(),
				true
			))
			.forEach(recruitmentParticipantTypeRepository::save);

		return recruitmentParticipantTypeRepository.findByRecruitmentEntityOrderByIdAsc(recruitmentEntity)
			.stream()
			.sorted(Comparator.comparing(rule -> rule.getParticipantTypeEntity().isStudent() ? 0 : 1))
			.toList();
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

	private int normalizePageSize(int pageSize) {
		if (pageSize < 10) {
			return 10;
		}
		if (pageSize > 100) {
			return 100;
		}

		return pageSize;
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
		return churches.stream()
			.collect(Collectors.toMap(
				RecruitmentChurchEntity::getId,
				participantRepository::countByRecruitmentChurchEntity
			));
	}

	private String buildParticipantsUrl(
		Long recruitmentId,
		boolean churchReport,
		boolean allReport,
		LectureType lectureType,
		LectureEntity selectedLecture,
		RecruitmentChurchEntity selectedChurch,
		int page,
		int pageSize
	) {
		StringBuilder url = new StringBuilder("/admin/recruitments/")
			.append(recruitmentId)
			.append("/participants?reportType=")
			.append(allReport ? "all" : (churchReport ? "church" : "lecture"));

		if (churchReport && selectedChurch != null) {
			url.append("&churchId=").append(selectedChurch.getId());
		}
		if (allReport) {
			url.append("&page=").append(Math.max(page, 0));
			url.append("&pageSize=").append(normalizePageSize(pageSize));
		}
		if (!churchReport && !allReport) {
			url.append("&lectureType=").append(lectureType.name());
			if (selectedLecture != null) {
				url.append("&lectureId=").append(selectedLecture.getId());
			}
		}

		return url.toString();
	}

	private String getAdminReturnUrl(
		Long recruitmentId,
		String returnUrl
	) {
		if (returnUrl != null && returnUrl.startsWith("/admin/")) {
			return returnUrl;
		}

		return "/admin/recruitment-dashboard/" + recruitmentId;
	}

	private byte[] createRecruitmentExcel(
		RecruitmentEntity recruitmentEntity,
		String downloadType,
		List<ParticipantEntity> participants,
		Map<Long, ParticipantLectureEntity> lectureByParticipantId,
		List<LectureEntity> lectures,
		List<RecruitmentChurchEntity> churches
	) throws IOException {
		try (XSSFWorkbook workbook = new XSSFWorkbook();
			 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
			CellStyle headerStyle = createHeaderStyle(workbook);

			if ("church".equals(downloadType)) {
				writeChurchParticipantSheets(workbook, headerStyle, churches, participants, lectureByParticipantId);
			} else {
				writeLectureParticipantSheets(workbook, headerStyle, lectures);
			}

			workbook.write(outputStream);

			return outputStream.toByteArray();
		}
	}

	private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
		Font font = workbook.createFont();
		font.setBold(true);

		CellStyle style = workbook.createCellStyle();
		style.setFont(font);

		return style;
	}

	private void writeLectureParticipantSheets(
		XSSFWorkbook workbook,
		CellStyle headerStyle,
		List<LectureEntity> lectures
	) {
		Set<String> sheetNames = new HashSet<>();

		for (LectureEntity lecture : lectures) {
			List<ParticipantLectureEntity> applications = findLectureApplications(lecture);
			Sheet sheet = workbook.createSheet(uniqueSheetName(sheetNames, getLectureSheetPrefix(lecture) + "_" + lecture.getSpeakerName()));
			String[] headers = {"번호", "이름", "구분", "교회", "전화번호"};
			createHeaderRow(sheet, headerStyle, headers);

			for (int index = 0; index < applications.size(); index++) {
				ParticipantLectureEntity application = applications.get(index);
				ParticipantEntity participant = application.getParticipantEntity();
				Row row = sheet.createRow(index + 1);

				writeCell(row, 0, index + 1);
				writeCell(row, 1, participant.getName());
				writeCell(row, 2, getParticipantTypeName(participant));
				writeCell(row, 3, participant.getRecruitmentChurchEntity().getName());
				writeCell(row, 4, participant.getPhoneNumber());
			}

			autoSize(sheet, headers.length);
		}
	}

	private void writeChurchParticipantSheets(
		XSSFWorkbook workbook,
		CellStyle headerStyle,
		List<RecruitmentChurchEntity> churches,
		List<ParticipantEntity> participants,
		Map<Long, ParticipantLectureEntity> lectureByParticipantId
	) {
		Set<String> sheetNames = new HashSet<>();

		for (RecruitmentChurchEntity church : churches) {
			List<ParticipantEntity> churchParticipants = participants.stream()
				.filter(participant -> participant.getRecruitmentChurchEntity().getId().equals(church.getId()))
				.toList();
			Sheet sheet = workbook.createSheet(uniqueSheetName(sheetNames, church.getName()));
			String[] headers = {"번호", "이름", "구분", "전화번호", "오전 강사", "오후 강사"};
			createHeaderRow(sheet, headerStyle, headers);

			for (int index = 0; index < churchParticipants.size(); index++) {
				ParticipantEntity participant = churchParticipants.get(index);
				ParticipantLectureEntity participantLecture = lectureByParticipantId.get(participant.getId());
				Row row = sheet.createRow(index + 1);

				writeCell(row, 0, index + 1);
				writeCell(row, 1, participant.getName());
				writeCell(row, 2, getParticipantTypeName(participant));
				writeCell(row, 3, participant.getPhoneNumber());
				writeCell(row, 4, getLectureName(participantLecture == null ? null : participantLecture.getMorningLectureEntity()));
				writeCell(row, 5, getLectureName(participantLecture == null ? null : participantLecture.getAfternoonLectureEntity()));
			}

			autoSize(sheet, headers.length);
		}
	}


	private void createHeaderRow(
		Sheet sheet,
		CellStyle headerStyle,
		String[] headers
	) {
		Row row = sheet.createRow(0);

		for (int index = 0; index < headers.length; index++) {
			row.createCell(index).setCellValue(headers[index]);
			row.getCell(index).setCellStyle(headerStyle);
		}
	}

	private void writeCell(
		Row row,
		int index,
		String value
	) {
		row.createCell(index).setCellValue(value == null ? "" : value);
	}

	private void writeCell(
		Row row,
		int index,
		int value
	) {
		row.createCell(index).setCellValue(value);
	}

	private void autoSize(
		Sheet sheet,
		int columnCount
	) {
		for (int index = 0; index < columnCount; index++) {
			sheet.autoSizeColumn(index);
			sheet.setColumnWidth(index, Math.min(sheet.getColumnWidth(index) + 768, 14000));
		}
	}

	private String getParticipantTypeName(ParticipantEntity participant) {
		return participant.getType().isStudent() ? "학생" : "교사";
	}

	private String getLectureName(LectureEntity lectureEntity) {
		return lectureEntity == null ? "미신청" : lectureEntity.getSpeakerName();
	}

	private String getLectureSheetPrefix(LectureEntity lectureEntity) {
		return lectureEntity.getType() == LectureType.AM ? "오전" : "오후";
	}

	private String sanitizeFilename(String filename) {
		return filename.replaceAll("[\\\\/:*?\"<>|]", "_");
	}

	private String uniqueSheetName(
		Set<String> sheetNames,
		String requestedName
	) {
		String baseName = sanitizeSheetName(requestedName);
		String sheetName = truncateSheetName(baseName);
		int suffix = 2;

		while (sheetNames.contains(sheetName)) {
			String suffixText = "_" + suffix;
			sheetName = truncateSheetName(baseName, suffixText.length()) + suffixText;
			suffix++;
		}

		sheetNames.add(sheetName);

		return sheetName;
	}

	private String sanitizeSheetName(String sheetName) {
		String sanitized = sheetName.replaceAll("[\\\\/?*\\[\\]:]", "_").trim();

		if (sanitized.isBlank()) {
			return "Sheet";
		}

		return sanitized;
	}

	private String truncateSheetName(String sheetName) {
		return truncateSheetName(sheetName, 0);
	}

	private String truncateSheetName(
		String sheetName,
		int reservedLength
	) {
		int maxLength = 31 - reservedLength;

		if (sheetName.length() <= maxLength) {
			return sheetName;
		}

		return sheetName.substring(0, maxLength);
	}

	private void decreaseIfPresent(LectureEntity lectureEntity) {
		if (lectureEntity != null) {
			lectureEntity.decreaseParticipantCount();
		}
	}
}
