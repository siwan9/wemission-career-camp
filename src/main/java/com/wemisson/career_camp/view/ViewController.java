package com.wemisson.career_camp.view;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.wemisson.career_camp.domain.admin.service.query.AdminRecruitmentQueryService;
import com.wemisson.career_camp.domain.admin.service.query.AdminViewService;
import com.wemisson.career_camp.domain.participant.dto.ParticipantLookupResult;
import com.wemisson.career_camp.domain.participant.session.ParticipantSession;
import com.wemisson.career_camp.domain.participant.service.command.ParticipantLookupService;
import com.wemisson.career_camp.domain.participant.service.query.ParticipantRegistrationViewService;
import com.wemisson.career_camp.domain.recruitment.service.query.RecruitmentQueryService;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;
import com.wemisson.career_camp.domain.recruitment.service.query.LectureQueryService;
import com.wemisson.career_camp.domain.participant.service.command.LectureApplicationService;
import com.wemisson.career_camp.domain.participant.service.draft.DraftExitReleaseService;
import com.wemisson.career_camp.domain.recruitment.dto.LectureType;
import com.wemisson.career_camp.domain.participant.dto.ParticipantCreateRequest;
import com.wemisson.career_camp.domain.participant.entity.ParticipantLectureDraftEntity;

import static com.wemisson.career_camp.domain.admin.controller.AdminAuthController.ADMIN_ID_SESSION_KEY;
import static com.wemisson.career_camp.domain.admin.controller.AdminAuthController.ADMIN_NAME_SESSION_KEY;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ViewController {

	private final RecruitmentQueryService recruitmentService;
	private final LectureQueryService lectureService;
	private final LectureApplicationService lectureApplicationService;
	private final ParticipantRegistrationViewService participantRegistrationViewService;
	private final ParticipantLookupService participantLookupService;
	private final AdminViewService adminViewService;
	private final AdminRecruitmentQueryService adminRecruitmentQueryService;
	private final ParticipantSession participantSession;
	private final DraftExitReleaseService draftExitReleaseService;

	@GetMapping("/home")
	public String home(
		Model model,
		HttpSession session
	) {
		clearRegistration(session);
		participantSession.clearLookup(session);
		RecruitmentEntity currentRecruitment = recruitmentService.findVisibleRecruitment()
			.orElse(null);

		model.addAttribute("recruitment", currentRecruitment);

		return "home";
	}

	@GetMapping("/register")
	public String register(
		Model model,
		HttpSession session,
		RedirectAttributes redirectAttributes
	) {
		ParticipantCreateRequest existingRequest = participantSession.registrationRequest(session);

		if (participantSession.registrationRecruitmentId(session) == null && existingRequest == null) {
			clearRegistration(session);
		}
		RecruitmentEntity recruitmentEntity = findRegistrationFlowRecruitment(session);

		if (recruitmentEntity == null) {
			redirectAttributes.addFlashAttribute("errorMessage", "현재 신청 가능한 모집이 없습니다.");

			return "redirect:/home";
		}

		model.addAttribute(
			"request",
			existingRequest == null ? ParticipantCreateRequest.createEmpty() : existingRequest
		);
		model.addAttribute("adminReturnUrl", participantSession.adminReturnUrl(session));
		addRegisterAttributes(model, false, recruitmentEntity);
		return "register";
	}

	@GetMapping("/register/edit")
	public String editRegister(
		Model model,
		HttpSession session
	) {
		Long editingParticipantId = participantSession.editingParticipantId(session);

		if (editingParticipantId == null) {
			return "redirect:/lookup";
		}

		ParticipantRegistrationViewService.ParticipantEditView editView = participantRegistrationViewService.findParticipantEditView(
			editingParticipantId
		);

		model.addAttribute("request", editView.request());
		addRegisterAttributes(model, true, editView.recruitment());
		model.addAttribute("adminReturnUrl", participantSession.adminReturnUrl(session));

		return "register";
	}

	@GetMapping("/lectures")
	public String lectures(Model model) {
		RecruitmentEntity recruitmentEntity = recruitmentService.findVisibleRecruitment()
			.orElse(null);

		if (recruitmentEntity == null) {
			return "redirect:/home";
		}

		model.addAttribute("recruitment", recruitmentEntity);
		LectureQueryService.LectureSelection lectureSelection = lectureService.findLectures(
			recruitmentEntity,
			true,
			true
		);
		model.addAttribute("morningLectures", lectureSelection.morningLectures());
		model.addAttribute("afternoonLectures", lectureSelection.afternoonLectures());

		return "lectures";
	}

	@GetMapping("/lookup")
	public String lookupPage(
		Model model,
		HttpSession session
	) {
		ParticipantSession.LookupFlow lookupFlow = participantSession.lookupFlow(session);

		if (!lookupFlow.hasCondition()) {
			addLookupPageAttributes(model);
			return "lookup";
		}

		return addLookupResults(
			lookupFlow.name(),
			lookupFlow.churchId(),
			lookupFlow.phoneNumber(),
			model,
			session
		);
	}

	@PostMapping("/lookup")
	public String lookup(
		@RequestParam String name,
		@RequestParam Long churchId,
		@RequestParam String phoneNumber,
		Model model,
		HttpSession session
	) {
		participantSession.saveLookupCondition(session, name, churchId, phoneNumber);

		return addLookupResults(name, churchId, phoneNumber, model, session);
	}

	@GetMapping("/registration/complete")
	public String registrationComplete(
		Model model,
		HttpSession session
	) {
		Long participantLectureId = participantSession.participantLectureId(session);

		if (participantLectureId == null || participantSession.isLectureEditMode(session)) {
			return "redirect:/lookup";
		}

		model.addAttribute(
			"completeView",
			participantLookupService.findRegistrationCompleteView(participantLectureId)
		);
		model.addAttribute("adminReturnUrl", participantSession.adminReturnUrl(session));

		return "registration-complete";
	}

	@GetMapping("/admin/home")
	public String adminHome(
		Model model,
		HttpSession session
	) {
		AdminViewService.AdminHomeView adminHomeView = adminViewService.findAdminHomeView();
		String adminName = (String)session.getAttribute(ADMIN_NAME_SESSION_KEY);

		model.addAttribute("adminName", adminName);
		model.addAttribute("currentStatusDescription", adminHomeView.currentStatusDescription());
		model.addAttribute("recruitmentGroups", adminHomeView.recruitmentGroups());
		model.addAttribute("participantCounts", adminHomeView.participantCounts());
		model.addAttribute("totalRecruitmentCount", adminHomeView.totalRecruitmentCount());

		return "admin/home";
	}

	@GetMapping("/admin/recruitment-dashboard/{recruitmentId}")
	public String adminRecruitmentDashboard(
		@PathVariable Long recruitmentId,
		@RequestParam(required = false) String phoneNumber,
		Model model
	) {
		AdminViewService.AdminRecruitmentDashboardView dashboardView = adminViewService
			.findRecruitmentDashboardView(recruitmentId, phoneNumber);

		model.addAttribute("recruitment", dashboardView.recruitment());
		model.addAttribute("nextRecruitmentStatusDescription", dashboardView.nextRecruitmentStatusDescription());
		model.addAttribute("participantCount", dashboardView.participantCount());
		model.addAttribute("morningApplicationCount", dashboardView.morningApplicationCount());
		model.addAttribute("afternoonApplicationCount", dashboardView.afternoonApplicationCount());
		model.addAttribute("lectureCount", dashboardView.lectureCount());
		model.addAttribute("morningLectures", dashboardView.lectureSelection().morningLectures());
		model.addAttribute("afternoonLectures", dashboardView.lectureSelection().afternoonLectures());
		model.addAttribute("phoneNumber", phoneNumber);
		model.addAttribute("quickResults", dashboardView.quickResults());

		return "admin/recruitment-dashboard";
	}

	@GetMapping("/admin/recruitments/{recruitmentId}/participants")
	public String adminParticipants(
		@PathVariable Long recruitmentId,
		@RequestParam(required = false, defaultValue = "all") String reportType,
		@RequestParam(required = false, defaultValue = "AM") LectureType lectureType,
		@RequestParam(required = false) Long lectureId,
		@RequestParam(required = false) Long churchId,
		@RequestParam(required = false, defaultValue = "0") int page,
		@RequestParam(required = false, defaultValue = "50") int pageSize,
		Model model
	) {
		AdminRecruitmentQueryService.ParticipantsView participantsView =
			adminRecruitmentQueryService.findParticipantsView(
				recruitmentId,
				reportType,
				lectureType,
				lectureId,
				churchId,
				page,
				pageSize
			);

		model.addAttribute("recruitment", participantsView.recruitment());
		model.addAttribute("reportType", participantsView.reportType());
		model.addAttribute("lectureType", participantsView.lectureType());
		model.addAttribute("lectures", participantsView.lectures());
		model.addAttribute("filteredLectures", participantsView.filteredLectures());
		model.addAttribute("churches", participantsView.churches());
		model.addAttribute("allParticipants", participantsView.allParticipants());
		model.addAttribute("allParticipantPage", participantsView.allParticipantPage());
		model.addAttribute("currentPage", participantsView.currentPage());
		model.addAttribute("pageSize", participantsView.pageSize());
		model.addAttribute("pageNumbers", participantsView.pageNumbers());
		model.addAttribute("selectedLecture", participantsView.selectedLecture());
		model.addAttribute("selectedChurch", participantsView.selectedChurch());
		model.addAttribute("selectedLectureApplications", participantsView.selectedLectureApplications());
		model.addAttribute("selectedChurchParticipants", participantsView.selectedChurchParticipants());
		model.addAttribute("churchParticipantLectures", participantsView.churchParticipantLectures());
		model.addAttribute("lectureCounts", participantsView.lectureCounts());
		model.addAttribute("churchCounts", participantsView.churchCounts());
		model.addAttribute("currentParticipantsUrl", participantsView.currentParticipantsUrl());

		return "admin/participants";
	}

	@GetMapping("/admin/recruitments/{recruitmentId}/settings")
	public String adminSettings(
		@PathVariable Long recruitmentId,
		Model model
	) {
		AdminRecruitmentQueryService.SettingsView settingsView = adminRecruitmentQueryService
			.findSettingsView(recruitmentId);

		model.addAttribute("recruitment", settingsView.recruitment());
		model.addAttribute("lectures", settingsView.lectures());
		model.addAttribute("churches", settingsView.churches());
		model.addAttribute("participantTypeRules", settingsView.participantTypeRules());

		return "admin/settings";
	}

	@GetMapping("/lecture")
	public String lecture(
		HttpSession session,
		Model model
	) {
		ParticipantCreateRequest request = participantSession.registrationRequest(session);
		draftExitReleaseService.cancelRelease(participantSession.draftToken(session));

		if (request == null) {
			return "redirect:/register";
		}
		if (
			participantSession.participantLectureId(session) != null
				&& !participantSession.isLectureEditMode(session)
		) {
			return "redirect:/registration/complete";
		}

		RecruitmentEntity recruitmentEntity = findLectureFlowRecruitment(session);

		if (recruitmentEntity == null) {
			return "redirect:/home";
		}

		RegistrationContext registrationContext = createRegistrationContext(recruitmentEntity, request);
		boolean canSelectMorningLecture = registrationContext.canSelectMorningLecture();
		boolean canSelectAfternoonLecture = registrationContext.canSelectAfternoonLecture();

		if (!canSelectMorningLecture && !canSelectAfternoonLecture) {
			participantSession.clearRegistration(session);

			return "redirect:/register";
		}

		model.addAttribute("recruitment", recruitmentEntity);
		addRecruitmentNoticeLines(model, recruitmentEntity);
		model.addAttribute("canSelectMorningLecture", canSelectMorningLecture);
		model.addAttribute("canSelectAfternoonLecture", canSelectAfternoonLecture);
		model.addAttribute("adminReturnUrl", participantSession.adminReturnUrl(session));
		model.addAttribute("adminLectureMode", isAdmin(session));
		LectureQueryService.LectureSelection lectureSelection = lectureService.findLectures(
			recruitmentEntity,
			canSelectMorningLecture,
			canSelectAfternoonLecture
		);
		model.addAttribute("morningLectures", lectureSelection.morningLectures());
		model.addAttribute("afternoonLectures", lectureSelection.afternoonLectures());
		addAppliedLectureIds(session, model);
		String draftToken = participantSession.getOrCreateDraftToken(session);
		addDraftLectureIds(draftToken, model);
		model.addAttribute(
			"readyToFinalize",
				lectureApplicationService.isReadyToFinalize(
					request,
					participantSession.participantLectureId(session),
					recruitmentEntity.getId(),
					draftToken
			)
		);
		model.addAttribute(
			"editLectureMode",
			participantSession.isLectureEditMode(session)
		);

		return "lecture";
	}

	private void addAppliedLectureIds(HttpSession session, Model model) {
		ParticipantRegistrationViewService.SelectedLectureIds selectedLectureIds =
			participantRegistrationViewService.findSelectedLectureIds(
				participantSession.participantLectureId(session)
			);

		if (selectedLectureIds.morningLectureId() != null) {
			model.addAttribute("selectedMorningLectureId", selectedLectureIds.morningLectureId());
		}
		if (selectedLectureIds.afternoonLectureId() != null) {
			model.addAttribute("selectedAfternoonLectureId", selectedLectureIds.afternoonLectureId());
		}
	}

	private void addDraftLectureIds(String draftToken, Model model) {
		List<ParticipantLectureDraftEntity> activeDrafts = lectureApplicationService.findActiveDrafts(draftToken);

		model.addAttribute("hasDraftSelection", !activeDrafts.isEmpty());
		model.addAttribute(
			"draftExpiresAt",
			activeDrafts.stream()
				.map(ParticipantLectureDraftEntity::getExpiresAt)
				.min(LocalDateTime::compareTo)
				.orElse(null)
		);

		activeDrafts.forEach(draft -> {
			if (draft.getLectureType() == LectureType.AM) {
				model.addAttribute("selectedMorningLectureId", draft.getLectureEntity().getId());
				model.addAttribute("draftMorningLectureId", draft.getLectureEntity().getId());
				model.addAttribute("draftMorningExpiresAt", draft.getExpiresAt());
				return;
			}

			model.addAttribute("selectedAfternoonLectureId", draft.getLectureEntity().getId());
			model.addAttribute("draftAfternoonLectureId", draft.getLectureEntity().getId());
			model.addAttribute("draftAfternoonExpiresAt", draft.getExpiresAt());
		});
	}

	private RecruitmentEntity findLectureFlowRecruitment(HttpSession session) {
		if (participantSession.isLectureEditMode(session)) {
			Long participantLectureId = participantSession.participantLectureId(session);

			if (participantLectureId == null) {
				return null;
			}

			return participantRegistrationViewService.findRecruitmentForParticipantLecture(participantLectureId);
		}

		return findRegistrationFlowRecruitment(session);
	}

	private RecruitmentEntity findRegistrationFlowRecruitment(HttpSession session) {
		Long registrationRecruitmentId = participantSession.registrationRecruitmentId(session);

		if (registrationRecruitmentId != null) {
			return recruitmentService.findRecruitment(registrationRecruitmentId);
		}

		return recruitmentService.findCurrentRecruitment()
			.orElse(null);
	}

	private String addLookupResults(
		String name,
		Long churchId,
		String phoneNumber,
		Model model,
		HttpSession session
	) {
		RecruitmentEntity recruitmentEntity = recruitmentService.findVisibleRecruitment()
			.orElse(null);

		addLookupPageAttributes(model, recruitmentEntity);
		if (name == null || name.isBlank() || !phoneNumber.matches("\\d{10,11}")) {
			model.addAttribute("name", name);
			model.addAttribute("churchId", churchId);
			model.addAttribute("phoneNumber", phoneNumber);
			model.addAttribute("searched", true);
			model.addAttribute("results", List.of());
			model.addAttribute(
				"lookupErrorMessage",
				"이름과 10~11자리 전화번호를 입력해주세요."
			);

			return "lookup";
		}

		if (recruitmentEntity == null) {
			model.addAttribute("name", name);
			model.addAttribute("churchId", churchId);
			model.addAttribute("phoneNumber", phoneNumber);
			model.addAttribute("searched", true);
			model.addAttribute("results", List.of());
			model.addAttribute("lookupErrorMessage", "조회 가능한 모집이 없습니다.");

			return "lookup";
		}

		if (!recruitmentService.isSelectableChurch(recruitmentEntity, churchId)) {
			model.addAttribute("name", name);
			model.addAttribute("churchId", churchId);
			model.addAttribute("phoneNumber", phoneNumber);
			model.addAttribute("searched", true);
			model.addAttribute("results", List.of());
			model.addAttribute("lookupErrorMessage", "선택한 교회를 찾을 수 없습니다.");

			return "lookup";
		}

		List<ParticipantLookupResult> results = participantLookupService.lookup(
			recruitmentEntity,
			name.strip(),
			churchId,
			phoneNumber
		);

		model.addAttribute("name", name);
		model.addAttribute("churchId", churchId);
		model.addAttribute("phoneNumber", phoneNumber);
		model.addAttribute("results", results);
		model.addAttribute("searched", true);
		participantSession.saveLookupResultIds(
			session,
			results.stream()
				.map(ParticipantLookupResult::participantLectureId)
				.toList()
		);

		return "lookup";
	}

	private void addLookupPageAttributes(Model model) {
		RecruitmentEntity recruitmentEntity = recruitmentService.findVisibleRecruitment()
			.orElse(null);

		addLookupPageAttributes(model, recruitmentEntity);
	}

	private void addLookupPageAttributes(
		Model model,
		RecruitmentEntity recruitmentEntity
	) {
		model.addAttribute(
			"churches",
			recruitmentEntity == null ? List.of() : recruitmentService.findChurches(recruitmentEntity)
		);
	}

	private RegistrationContext createRegistrationContext(
		RecruitmentEntity recruitmentEntity,
		ParticipantCreateRequest request
	) {
		RecruitmentQueryService.ParticipantTypeRule participantTypeRule = recruitmentService.findParticipantTypeRule(
			recruitmentEntity,
			request.participantTypeId()
		);

		return new RegistrationContext(
			participantTypeRule.canSelectMorningLecture(),
			participantTypeRule.canSelectAfternoonLecture()
		);
	}

	private record RegistrationContext(
		boolean canSelectMorningLecture,
		boolean canSelectAfternoonLecture
	) {
	}

	private void addRegisterAttributes(
		Model model,
		boolean editMode,
		RecruitmentEntity recruitmentEntity
	) {
		List<?> participantTypes = participantRegistrationViewService.findSelectableParticipantTypes(recruitmentEntity);
		List<?> churches = participantRegistrationViewService.findSelectableChurches(recruitmentEntity);

		model.addAttribute("editMode", editMode);
		addRecruitmentNoticeLines(model, recruitmentEntity);
		model.addAttribute("participantTypes", participantTypes);
		model.addAttribute("churches", churches);
		model.addAttribute("canSubmitRegistration", !participantTypes.isEmpty() && !churches.isEmpty());
	}

	private void addRecruitmentNoticeLines(Model model, RecruitmentEntity recruitmentEntity) {
		model.addAttribute(
			"recruitmentNoticeLines",
			recruitmentService.findNoticeLines(recruitmentEntity)
		);
	}

	private void clearRegistration(HttpSession session) {
		lectureApplicationService.releaseDrafts(participantSession.draftToken(session));
		participantSession.clearRegistration(session);
	}

	private boolean isAdmin(HttpSession session) {
		return session.getAttribute(ADMIN_ID_SESSION_KEY) != null;
	}

}
