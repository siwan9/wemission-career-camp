package com.wemisson.career_camp.view;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.wemisson.career_camp.domain.participant.entity.ParticipantEntity;
import com.wemisson.career_camp.domain.participant.entity.ParticipantLectureEntity;
import com.wemisson.career_camp.domain.participant.entity.ParticipantTypeEntity;
import com.wemisson.career_camp.domain.participant.repository.ParticipantLectureRepository;
import com.wemisson.career_camp.domain.participant.repository.ParticipantRepository;
import com.wemisson.career_camp.domain.participant.repository.ParticipantTypeRepository;
import com.wemisson.career_camp.domain.recruitment.service.RecruitmentService;
import com.wemisson.career_camp.domain.recruitment.dto.LectureType;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentParticipantTypeEntity;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentParticipantTypeRepository;
import com.wemisson.career_camp.domain.recruitment.service.LectureService;
import com.wemisson.career_camp.domain.participant.dto.ParticipantCreateRequest;
import com.wemisson.career_camp.domain.admin.controller.AdminRecruitmentController;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentRepository;
import com.wemisson.career_camp.domain.recruitment.repository.LectureRepository;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentChurchEntity;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentChurchRepository;

import static com.wemisson.career_camp.domain.admin.controller.AdminAuthController.ADMIN_NAME_SESSION_KEY;
import static com.wemisson.career_camp.domain.admin.controller.AdminRecruitmentController.ADMIN_RETURN_URL_SESSION_KEY;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ViewController {

	private static final String REGISTRATION_REQUEST_SESSION_KEY = "registrationRequest";
	private static final String PARTICIPANT_LECTURE_ID_SESSION_KEY = "participantLectureId";
	private static final String EDITING_PARTICIPANT_ID_SESSION_KEY = "editingParticipantId";
	private static final String RETURN_TO_LOOKUP_AFTER_EDIT_SESSION_KEY = "returnToLookupAfterEdit";
	private static final String LOOKUP_PARTICIPANT_LECTURE_IDS_SESSION_KEY = "lookupParticipantLectureIds";
	private static final String LOOKUP_PHONE_NUMBER_SESSION_KEY = "lookupPhoneNumber";
	private static final String LOOKUP_PASSWORD_SESSION_KEY = "lookupPassword";

	private final RecruitmentService recruitmentService;
	private final LectureService lectureService;
	private final ParticipantLectureRepository participantLectureRepository;
	private final ParticipantRepository participantRepository;
	private final ParticipantTypeRepository participantTypeRepository;
	private final RecruitmentParticipantTypeRepository recruitmentParticipantTypeRepository;
	private final RecruitmentRepository recruitmentRepository;
	private final LectureRepository lectureRepository;
	private final RecruitmentChurchRepository recruitmentChurchRepository;
	private final AdminRecruitmentController adminRecruitmentController;

	@GetMapping("/home")
	public String home(
		Model model,
		HttpSession session
	) {
		clearLookupSession(session);
		RecruitmentEntity currentRecruitment = recruitmentService.findCurrentRecruitment()
			.orElse(null);

		model.addAttribute("recruitment", currentRecruitment);
		model.addAttribute("recruitmentEntity", currentRecruitment);

		return "home";
	}

	private void clearLookupSession(HttpSession session) {
		session.removeAttribute(LOOKUP_PARTICIPANT_LECTURE_IDS_SESSION_KEY);
		session.removeAttribute(LOOKUP_PHONE_NUMBER_SESSION_KEY);
		session.removeAttribute(LOOKUP_PASSWORD_SESSION_KEY);
		session.removeAttribute(RETURN_TO_LOOKUP_AFTER_EDIT_SESSION_KEY);
	}

	@GetMapping("/register")
	public String register(
		Model model,
		HttpSession session
	) {
		session.removeAttribute(EDITING_PARTICIPANT_ID_SESSION_KEY);
		session.removeAttribute(PARTICIPANT_LECTURE_ID_SESSION_KEY);
		session.removeAttribute(REGISTRATION_REQUEST_SESSION_KEY);

		model.addAttribute("request", ParticipantCreateRequest.createEmpty());
		addRegisterAttributes(model, false);
		return "register";
	}

	@GetMapping("/register/edit")
	public String editRegister(
		Model model,
		HttpSession session
	) {
		Long editingParticipantId = (Long)session.getAttribute(
			EDITING_PARTICIPANT_ID_SESSION_KEY
		);

		if (editingParticipantId == null) {
			return "redirect:/lookup";
		}

		ParticipantEntity participantEntity = participantRepository.findById(editingParticipantId)
			.orElseThrow(() -> new IllegalArgumentException("참가자 정보를 찾을 수 없습니다."));

		model.addAttribute("request", toParticipantCreateRequest(participantEntity));
		addRegisterAttributes(model, true);
		model.addAttribute("adminReturnUrl", session.getAttribute(ADMIN_RETURN_URL_SESSION_KEY));

		return "register";
	}

	@GetMapping("/lectures")
	public String lectures(Model model) {
		RecruitmentEntity recruitmentEntity = recruitmentService.findCurrentRecruitment()
			.orElse(null);

		if (recruitmentEntity == null) {
			return "redirect:/home";
		}

		model.addAttribute("recruitment", recruitmentEntity);
		model.addAttribute("recruitmentEntity", recruitmentEntity);
		model.addAttribute(
			"morningLectures",
			lectureService.findOpenLectures(recruitmentEntity, LectureType.AM)
		);
		model.addAttribute(
			"afternoonLectures",
			lectureService.findOpenLectures(recruitmentEntity, LectureType.PM)
		);

		return "lectures";
	}

	@GetMapping("/admin/login")
	public String adminLogin() {
		return "redirect:/home";
	}

	@GetMapping("/admin/home")
	public String adminHome(
		Model model,
		HttpSession session
	) {
		List<RecruitmentEntity> recruitments = recruitmentRepository.findAllByOrderByIdDesc();
		Map<Long, Integer> participantCounts = recruitments.stream()
			.collect(Collectors.toMap(
				RecruitmentEntity::getId,
				participantRepository::countByRecruitmentEntity
			));

		String adminName = (String)session.getAttribute(ADMIN_NAME_SESSION_KEY);

		model.addAttribute("adminName", adminName);
		model.addAttribute("currentRecruitment", recruitments.stream()
			.filter(RecruitmentEntity::isOpen)
			.findFirst()
			.orElse(null));
		model.addAttribute("pastRecruitments", recruitments.stream()
			.filter(recruitment -> !recruitment.isOpen())
			.toList());
		model.addAttribute("participantCounts", participantCounts);
		model.addAttribute("totalRecruitmentCount", recruitments.size());
		model.addAttribute("totalParticipantCount", participantCounts.values()
			.stream()
			.mapToInt(Integer::intValue)
			.sum());

		return "admin/home";
	}

	@PostMapping("/admin/recruitments")
	public String createRecruitment(
		@RequestParam String name,
		@RequestParam String description,
		@RequestParam String notice,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
		RedirectAttributes redirectAttributes
	) {
		RecruitmentEntity recruitmentEntity = recruitmentRepository.save(
			RecruitmentEntity.create(name, description, notice, startAt, endAt, false)
		);
		createParticipantTypeRules(recruitmentEntity);
		redirectAttributes.addFlashAttribute("successMessage", "모집이 추가되었습니다.");

		return "redirect:/admin/home";
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/delete")
	@org.springframework.transaction.annotation.Transactional
	public String deleteRecruitment(
		@PathVariable Long recruitmentId,
		RedirectAttributes redirectAttributes
	) {
		RecruitmentEntity recruitmentEntity = recruitmentRepository.findById(recruitmentId)
			.orElseThrow(() -> new IllegalArgumentException("모집을 찾을 수 없습니다."));

		participantLectureRepository.deleteByParticipantEntityRecruitmentEntity(recruitmentEntity);
		participantRepository.deleteByRecruitmentEntity(recruitmentEntity);
		lectureRepository.deleteByRecruitmentEntity(recruitmentEntity);
		recruitmentChurchRepository.deleteByRecruitmentEntity(recruitmentEntity);
		recruitmentParticipantTypeRepository.deleteByRecruitmentEntity(recruitmentEntity);
		recruitmentRepository.delete(recruitmentEntity);
		redirectAttributes.addFlashAttribute("successMessage", "모집이 삭제되었습니다.");

		return "redirect:/admin/home";
	}

	@GetMapping("/admin/recruitment-dashboard/{recruitmentId}")
	public String adminRecruitmentDashboard(
		@PathVariable Long recruitmentId,
		@RequestParam(required = false) String phoneNumber,
		Model model,
		HttpSession session
	) {
		RecruitmentEntity recruitmentEntity = recruitmentRepository.findById(recruitmentId)
			.orElseThrow(() -> new IllegalArgumentException("모집을 찾을 수 없습니다."));
		int participantCount = participantRepository.countByRecruitmentEntity(recruitmentEntity);
		int morningApplicationCount = participantLectureRepository.countMorningApplications(recruitmentEntity);
		int afternoonApplicationCount = participantLectureRepository.countAfternoonApplications(recruitmentEntity);

		model.addAttribute("adminName", session.getAttribute(ADMIN_NAME_SESSION_KEY));
		model.addAttribute("recruitment", recruitmentEntity);
		model.addAttribute("participantCount", participantCount);
		model.addAttribute("morningApplicationCount", morningApplicationCount);
		model.addAttribute("afternoonApplicationCount", afternoonApplicationCount);
		model.addAttribute("lectureCount", lectureRepository.countByRecruitmentEntity(recruitmentEntity));
		model.addAttribute("morningLectures", lectureService.findOpenLectures(recruitmentEntity, LectureType.AM));
		model.addAttribute("afternoonLectures", lectureService.findOpenLectures(recruitmentEntity, LectureType.PM));
		model.addAttribute("phoneNumber", phoneNumber);
		model.addAttribute(
			"quickResults",
			adminRecruitmentController.searchParticipants(recruitmentEntity, phoneNumber)
		);

		return "admin/recruitment-dashboard";
	}
	@GetMapping("/lecture")
	public String lecture(
		HttpSession session,
		Model model
	) {
		ParticipantCreateRequest request = (ParticipantCreateRequest)session.getAttribute(
			REGISTRATION_REQUEST_SESSION_KEY
		);

		if (request == null) {
			return "redirect:/register";
		}

		RecruitmentEntity recruitmentEntity = recruitmentService.findCurrentRecruitment()
			.orElse(null);

		if (recruitmentEntity == null) {
			return "redirect:/home";
		}

		RecruitmentParticipantTypeEntity participantTypeRule = findParticipantTypeRule(
			recruitmentEntity,
			request.participantTypeId()
		);

		boolean canSelectMorningLecture = participantTypeRule.canSelectMorningLecture();
		boolean canSelectAfternoonLecture = participantTypeRule.canSelectAfternoonLecture();

		if (!canSelectMorningLecture && !canSelectAfternoonLecture) {
			session.removeAttribute(REGISTRATION_REQUEST_SESSION_KEY);

			return "redirect:/register";
		}

		model.addAttribute("participantType", participantTypeRule.getParticipantTypeEntity().getType());
		model.addAttribute("recruitment", recruitmentEntity);
		model.addAttribute("canSelectMorningLecture", canSelectMorningLecture);
		model.addAttribute("canSelectAfternoonLecture", canSelectAfternoonLecture);
		model.addAttribute("adminReturnUrl", session.getAttribute(ADMIN_RETURN_URL_SESSION_KEY));
		model.addAttribute("morningLectures", canSelectMorningLecture
			? lectureService.findOpenLectures(recruitmentEntity, LectureType.AM)
			: List.of());
		model.addAttribute("afternoonLectures", canSelectAfternoonLecture
			? lectureService.findOpenLectures(recruitmentEntity, LectureType.PM)
			: List.of());
		addAppliedLectureIds(session, model);
		model.addAttribute(
			"editLectureMode",
			session.getAttribute(PARTICIPANT_LECTURE_ID_SESSION_KEY) != null
		);

		return "lecture";
	}

	@PostMapping("/lecture")
	public String saveRegistrationToSession(
		@Valid @ModelAttribute("request") ParticipantCreateRequest request,
		BindingResult bindingResult,
		@RequestParam(required = false, defaultValue = "false") boolean confirmAdditional,
		Model model,
		HttpSession session
	) {
		if (bindingResult.hasErrors()) {
			addRegisterAttributes(model, false);
			return "register";
		}

		int existingParticipantCount = participantRepository.countByPhoneNumber(request.phoneNumber());

		if (existingParticipantCount > 0 && !confirmAdditional) {
			if (!participantRepository.existsByPhoneNumberAndPassword(
				request.phoneNumber(),
				request.password()
			)) {
				addDuplicateApplicationAttributes(
					model,
					existingParticipantCount,
					false,
					"추가 신청을 위해서 기존의 핀번호를 입력해주세요."
				);

				return "register";
			}

			addDuplicateApplicationAttributes(model, existingParticipantCount, true, null);

			return "register";
		}

		if (existingParticipantCount > 0) {
			if (!participantRepository.existsByPhoneNumberAndPassword(
				request.phoneNumber(),
				request.password()
			)) {
				addDuplicateApplicationAttributes(
					model,
					existingParticipantCount,
					false,
					"추가 신청을 위해서 기존의 핀번호를 입력해주세요."
				);

				return "register";
			}
		}

		session.removeAttribute(PARTICIPANT_LECTURE_ID_SESSION_KEY);
		session.setAttribute(REGISTRATION_REQUEST_SESSION_KEY, request);

		return "redirect:/lecture";
	}

	@PostMapping("/register/edit")
	public String updateParticipant(
		@Valid @ModelAttribute("request") ParticipantCreateRequest request,
		BindingResult bindingResult,
		Model model,
		HttpSession session
	) {
		if (bindingResult.hasErrors()) {
			addRegisterAttributes(model, true);

			return "register";
		}

		Long editingParticipantId = (Long)session.getAttribute(
			EDITING_PARTICIPANT_ID_SESSION_KEY
		);

		if (editingParticipantId == null) {
			return "redirect:/lookup";
		}

		ParticipantEntity participantEntity = participantRepository.findById(editingParticipantId)
			.orElseThrow(() -> new IllegalArgumentException("참가자 정보를 찾을 수 없습니다."));

		participantEntity.update(
			request.name(),
			findParticipantType(request.participantTypeId()),
			findRecruitmentChurch(request.churchId(), participantEntity.getRecruitmentEntity()),
			request.phoneNumber(),
			request.password()
		);
		participantRepository.save(participantEntity);

		session.removeAttribute(EDITING_PARTICIPANT_ID_SESSION_KEY);
		session.setAttribute(REGISTRATION_REQUEST_SESSION_KEY, request);

		String adminReturnUrl = (String)session.getAttribute(ADMIN_RETURN_URL_SESSION_KEY);
		if (adminReturnUrl != null) {
			session.removeAttribute(ADMIN_RETURN_URL_SESSION_KEY);
			return "redirect:" + adminReturnUrl;
		}

		if (Boolean.TRUE.equals(session.getAttribute(RETURN_TO_LOOKUP_AFTER_EDIT_SESSION_KEY))) {
			session.removeAttribute(RETURN_TO_LOOKUP_AFTER_EDIT_SESSION_KEY);
			return "redirect:/lookup";
		}

		return "redirect:/lecture";
	}

	private void addDuplicateApplicationAttributes(
		Model model,
		int existingParticipantCount,
		boolean duplicateApplicationPinVerified,
		String duplicateApplicationErrorMessage
	) {
		addRegisterAttributes(model, false);
		model.addAttribute("duplicateApplicationExists", true);
		model.addAttribute("existingParticipantCount", existingParticipantCount);
		model.addAttribute("duplicateApplicationPinVerified", duplicateApplicationPinVerified);
		model.addAttribute(
			"duplicateApplicationErrorMessage",
			duplicateApplicationErrorMessage
		);
	}

	private void addAppliedLectureIds(HttpSession session, Model model) {
		Long participantLectureId = (Long)session.getAttribute(
			PARTICIPANT_LECTURE_ID_SESSION_KEY
		);

		if (participantLectureId == null) {
			return;
		}

		participantLectureRepository.findById(participantLectureId)
			.ifPresent(participantLecture -> {
				model.addAttribute(
					"selectedMorningLectureId",
					getMorningLectureId(participantLecture)
				);
				model.addAttribute(
					"selectedAfternoonLectureId",
					getAfternoonLectureId(participantLecture)
				);
			});
	}

	private Long getMorningLectureId(ParticipantLectureEntity participantLectureEntity) {
		if (participantLectureEntity.getMorningLectureEntity() == null) {
			return null;
		}

		return participantLectureEntity.getMorningLectureEntity().getId();
	}

	private Long getAfternoonLectureId(ParticipantLectureEntity participantLectureEntity) {
		if (participantLectureEntity.getAfternoonLectureEntity() == null) {
			return null;
		}

		return participantLectureEntity.getAfternoonLectureEntity().getId();
	}

	private ParticipantCreateRequest toParticipantCreateRequest(ParticipantEntity participantEntity) {
		return new ParticipantCreateRequest(
			participantEntity.getName(),
			participantEntity.getType().getId(),
			participantEntity.getRecruitmentChurchEntity().getId(),
			participantEntity.getPhoneNumber(),
			participantEntity.getPassword()
		);
	}

	private void addRegisterAttributes(Model model, boolean editMode) {
		model.addAttribute("editMode", editMode);
		RecruitmentEntity recruitmentEntity = recruitmentService.findCurrentRecruitment().orElse(null);

		model.addAttribute("recruitment", recruitmentEntity);
		model.addAttribute("participantTypes", findSelectableParticipantTypes());
		model.addAttribute("churches", findSelectableChurches(recruitmentEntity));
	}

	private List<ParticipantTypeEntity> findSelectableParticipantTypes() {
		return recruitmentService.findCurrentRecruitment()
			.map(recruitment -> recruitmentParticipantTypeRepository.findByRecruitmentEntityOrderByIdAsc(recruitment)
				.stream()
				.filter(recruitmentParticipantTypeEntity ->
					recruitmentParticipantTypeEntity.canSelectMorningLecture()
						|| recruitmentParticipantTypeEntity.canSelectAfternoonLecture()
				)
				.map(RecruitmentParticipantTypeEntity::getParticipantTypeEntity)
				.toList())
			.filter(participantTypes -> !participantTypes.isEmpty())
			.orElseGet(participantTypeRepository::findAll);
	}

	private RecruitmentParticipantTypeEntity findParticipantTypeRule(
		RecruitmentEntity recruitmentEntity,
		Long participantTypeId
	) {
		return recruitmentParticipantTypeRepository
			.findByRecruitmentEntityAndParticipantTypeEntityId(recruitmentEntity, participantTypeId)
			.orElseThrow(() -> new IllegalArgumentException("현재 모집에서 신청할 수 없는 참가자 유형입니다."));
	}

	private ParticipantTypeEntity findParticipantType(Long participantTypeId) {
		return participantTypeRepository.findById(participantTypeId)
			.orElseThrow(() -> new IllegalArgumentException("참가자 유형을 찾을 수 없습니다."));
	}

	private List<RecruitmentChurchEntity> findSelectableChurches(RecruitmentEntity recruitmentEntity) {
		if (recruitmentEntity == null) {
			return List.of();
		}

		return recruitmentChurchRepository.findByRecruitmentEntityOrderBySortOrderAscIdAsc(recruitmentEntity);
	}

	private RecruitmentChurchEntity findRecruitmentChurch(
		Long churchId,
		RecruitmentEntity recruitmentEntity
	) {
		return recruitmentChurchRepository.findByIdAndRecruitmentEntity(churchId, recruitmentEntity)
			.orElseThrow(() -> new IllegalArgumentException("현재 모집에서 선택할 수 없는 교회입니다."));
	}

	private void createParticipantTypeRules(RecruitmentEntity recruitmentEntity) {
		participantTypeRepository.findAll()
			.forEach(participantType -> {
				boolean canSelectMorningLecture = participantType.isStudent();
				boolean canSelectAfternoonLecture = true;

				recruitmentParticipantTypeRepository.save(
					RecruitmentParticipantTypeEntity.create(
						recruitmentEntity,
						participantType,
						canSelectMorningLecture,
						canSelectAfternoonLecture
					)
				);
			});
	}
}
