package com.wemisson.career_camp.domain.participant.controller;

import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.wemisson.career_camp.domain.participant.dto.LectureApplicationResult;
import com.wemisson.career_camp.domain.participant.dto.ParticipantCreateRequest;
import com.wemisson.career_camp.domain.participant.dto.ParticipantLectureDraftResult;
import com.wemisson.career_camp.domain.participant.dto.ParticipantLectureDraftStatus;
import com.wemisson.career_camp.domain.participant.session.ParticipantSession;
import com.wemisson.career_camp.domain.participant.service.command.LectureApplicationService;
import com.wemisson.career_camp.domain.participant.service.draft.DraftExitReleaseService;
import com.wemisson.career_camp.domain.participant.service.query.ParticipantRegistrationViewService;
import com.wemisson.career_camp.domain.recruitment.dto.LectureType;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;
import com.wemisson.career_camp.domain.recruitment.service.query.LectureQueryService;
import com.wemisson.career_camp.domain.recruitment.service.query.RecruitmentQueryService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

import static com.wemisson.career_camp.domain.admin.controller.AdminAuthController.ADMIN_ID_SESSION_KEY;

@Controller
@RequiredArgsConstructor
public class LectureApplicationController {

	private final LectureApplicationService lectureApplicationService;
	private final ParticipantSession participantSession;
	private final RecruitmentQueryService recruitmentQueryService;
	private final LectureQueryService lectureQueryService;
	private final ParticipantRegistrationViewService participantRegistrationViewService;
	private final DraftExitReleaseService draftExitReleaseService;

	@PostMapping("/lectures/apply")
	public String apply(
		@RequestParam Long lectureId,
		HttpSession session,
		RedirectAttributes redirectAttributes
	) {
		ParticipantCreateRequest request = participantSession.registrationRequest(session);

		if (request == null) {
			return "redirect:/register";
		}
		cancelPendingExitRelease(session);

		try {
			RecruitmentEntity recruitmentEntity = requireLectureFlowRecruitment(session);
			lectureApplicationService.holdDraft(
				request,
				participantSession.participantLectureId(session),
				recruitmentEntity.getId(),
				lectureId,
				participantSession.getOrCreateDraftToken(session),
				isAdmin(session),
				participantSession.isLectureEditMode(session)
			);
			redirectAttributes.addFlashAttribute("successMessage", "특강이 임시 선택되었습니다.");
		} catch (RuntimeException e) {
			redirectAttributes.addFlashAttribute("errorMessage", getClientMessage(e));
		}

		return "redirect:/lecture";
	}

	@PostMapping(value = "/lectures/apply", headers = "X-Requested-With=XMLHttpRequest")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> applyAjax(
		@RequestParam Long lectureId,
		HttpSession session
	) {
		ParticipantCreateRequest request = participantSession.registrationRequest(session);

		if (request == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("success", false, "message", "참가자 정보를 먼저 입력해주세요."));
		}
		cancelPendingExitRelease(session);

		try {
			RecruitmentEntity recruitmentEntity = requireLectureFlowRecruitment(session);
			ParticipantLectureDraftResult result = lectureApplicationService.holdDraft(
				request,
				participantSession.participantLectureId(session),
				recruitmentEntity.getId(),
				lectureId,
				participantSession.getOrCreateDraftToken(session),
				isAdmin(session),
				participantSession.isLectureEditMode(session)
			);
			ParticipantLectureDraftStatus status = lectureApplicationService.getDraftStatus(
				request,
				participantSession.participantLectureId(session),
				recruitmentEntity.getId(),
				participantSession.getOrCreateDraftToken(session)
			);

			return ResponseEntity.ok(toDraftResponse(
				result,
				status,
				request,
				recruitmentEntity,
				"특강이 임시 선택되었습니다."
			));
		} catch (RuntimeException e) {
			return toFailureResponse(e);
		}
	}

	@PostMapping("/lectures/cancel")
	public String cancel(
		@RequestParam Long lectureId,
		HttpSession session,
		RedirectAttributes redirectAttributes
	) {
		ParticipantCreateRequest request = participantSession.registrationRequest(session);

		if (request == null) {
			return "redirect:/register";
		}
		cancelPendingExitRelease(session);

		try {
			RecruitmentEntity recruitmentEntity = requireLectureFlowRecruitment(session);
			lectureApplicationService.releaseDraft(
				request,
				participantSession.participantLectureId(session),
				recruitmentEntity.getId(),
				lectureId,
				participantSession.getOrCreateDraftToken(session)
			);
			redirectAttributes.addFlashAttribute("successMessage", "임시 선택이 취소되었습니다.");
		} catch (RuntimeException e) {
			redirectAttributes.addFlashAttribute("errorMessage", getClientMessage(e));
		}

		return "redirect:/lecture";
	}

	@PostMapping(value = "/lectures/cancel", headers = "X-Requested-With=XMLHttpRequest")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> cancelAjax(
		@RequestParam Long lectureId,
		HttpSession session
	) {
		ParticipantCreateRequest request = participantSession.registrationRequest(session);

		if (request == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("success", false, "message", "참가자 정보를 먼저 입력해주세요."));
		}
		cancelPendingExitRelease(session);

		try {
			RecruitmentEntity recruitmentEntity = requireLectureFlowRecruitment(session);
			ParticipantLectureDraftResult result = lectureApplicationService.releaseDraft(
				request,
				participantSession.participantLectureId(session),
				recruitmentEntity.getId(),
				lectureId,
				participantSession.getOrCreateDraftToken(session)
			);
			ParticipantLectureDraftStatus status = lectureApplicationService.getDraftStatus(
				request,
				participantSession.participantLectureId(session),
				recruitmentEntity.getId(),
				participantSession.getOrCreateDraftToken(session)
			);

			return ResponseEntity.ok(toDraftResponse(
				result,
				status,
				request,
				recruitmentEntity,
				"임시 선택이 취소되었습니다."
			));
		} catch (RuntimeException e) {
			return toFailureResponse(e);
		}
	}

	@GetMapping("/lectures/draft-status")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> draftStatus(HttpSession session) {
		ParticipantCreateRequest request = participantSession.registrationRequest(session);
		draftExitReleaseService.cancelRelease(participantSession.draftToken(session));

		if (request == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("success", false, "message", "참가자 정보를 먼저 입력해주세요."));
		}
		if (
			participantSession.participantLectureId(session) != null
				&& !participantSession.isLectureEditMode(session)
		) {
			return ResponseEntity.ok(Map.of(
				"success", true,
				"message", "신청이 완료되었습니다.",
				"completedApplication", true,
				"redirectUrl", "/registration/complete"
			));
		}

		try {
			RecruitmentEntity recruitmentEntity = requireLectureFlowRecruitment(session);
			ParticipantLectureDraftStatus status = lectureApplicationService.getDraftStatus(
				request,
				participantSession.participantLectureId(session),
				recruitmentEntity.getId(),
				participantSession.getOrCreateDraftToken(session)
			);

			return ResponseEntity.ok(toStatusResponse(
				status,
				request,
				recruitmentEntity,
				"현재 선택 상태를 확인했습니다."
			));
		} catch (RuntimeException e) {
			return toFailureResponse(e);
		}
	}

	@PostMapping("/lectures/finalize")
	public String finalizeApplication(
		HttpSession session,
		RedirectAttributes redirectAttributes
	) {
		ParticipantCreateRequest request = participantSession.registrationRequest(session);

		if (request == null) {
			return "redirect:/register";
		}
		cancelPendingExitRelease(session);

		try {
			RecruitmentEntity recruitmentEntity = requireLectureFlowRecruitment(session);
			LectureApplicationResult result = lectureApplicationService.finalizeDraft(
				request,
				participantSession.participantLectureId(session),
				recruitmentEntity.getId(),
				participantSession.getOrCreateDraftToken(session),
				isAdmin(session)
			);
			participantSession.finalizeLecture(session, result.participantLectureId());
			redirectAttributes.addFlashAttribute("lookupSuccessMessage", "특강 신청이 최종 완료되었습니다.");
		} catch (RuntimeException e) {
			redirectAttributes.addFlashAttribute("errorMessage", getClientMessage(e));
		}

		return "redirect:/registration/complete";
	}

	@PostMapping(value = "/lectures/finalize", headers = "X-Requested-With=XMLHttpRequest")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> finalizeApplicationAjax(HttpSession session) {
		ParticipantCreateRequest request = participantSession.registrationRequest(session);

		if (request == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("success", false, "message", "참가자 정보를 먼저 입력해주세요."));
		}
		cancelPendingExitRelease(session);

		try {
			RecruitmentEntity recruitmentEntity = requireLectureFlowRecruitment(session);
			LectureApplicationResult result = lectureApplicationService.finalizeDraft(
				request,
				participantSession.participantLectureId(session),
				recruitmentEntity.getId(),
				participantSession.getOrCreateDraftToken(session),
				isAdmin(session)
			);
			participantSession.finalizeLecture(session, result.participantLectureId());

			return ResponseEntity.ok(Map.of(
				"success", true,
				"message", "특강 신청이 최종 완료되었습니다.",
				"participantLectureId", result.participantLectureId(),
				"redirectUrl", "/registration/complete"
			));
		} catch (RuntimeException e) {
			return toFailureResponse(e);
		}
	}

	@PostMapping("/lectures/drafts/release")
	@ResponseBody
	public ResponseEntity<Void> releaseDrafts(HttpSession session) {
		draftExitReleaseService.scheduleRelease(participantSession.draftToken(session));

		return ResponseEntity.accepted().build();
	}

	private Map<String, Object> toDraftResponse(
		ParticipantLectureDraftResult result,
		ParticipantLectureDraftStatus status,
		ParticipantCreateRequest request,
		RecruitmentEntity recruitmentEntity,
		String message
	) {
		Map<String, Object> response = new java.util.LinkedHashMap<>();
		response.put("success", true);
		response.put("message", message);
		response.put("selected", result.selected());
		response.put("readyToFinalize", result.readyToFinalize());
		response.put("lectureId", result.lectureId());
		response.put("lectureType", result.lectureType().name());
		response.put("remainingCapacity", result.remainingCapacity());
		response.put("draftExpiresAt", status.expiresAt());
		response.put("selectedLectures", toSelectedLectureResponse(status.selectedLectures()));
		response.put("lectureAvailabilities", toLectureAvailabilityResponse(request, recruitmentEntity));

		return response;
	}

	private Map<String, Object> toStatusResponse(
		ParticipantLectureDraftStatus status,
		ParticipantCreateRequest request,
		RecruitmentEntity recruitmentEntity,
		String message
	) {
		Map<String, Object> response = new java.util.LinkedHashMap<>();
		response.put("success", true);
		response.put("message", message);
		response.put("readyToFinalize", status.readyToFinalize());
		response.put("draftExpiresAt", status.expiresAt());
		response.put("selectedLectures", toSelectedLectureResponse(status.selectedLectures()));
		response.put("lectureAvailabilities", toLectureAvailabilityResponse(request, recruitmentEntity));

		return response;
	}

	private Map<String, Object> toLectureAvailabilityResponse(
		ParticipantCreateRequest request,
		RecruitmentEntity recruitmentEntity
	) {
		RecruitmentQueryService.ParticipantTypeRule rule = recruitmentQueryService.findParticipantTypeRule(
			recruitmentEntity,
			request.participantTypeId()
		);
		LectureQueryService.LectureSelection lectureSelection = lectureQueryService.findLectures(
			recruitmentEntity,
			rule.canSelectMorningLecture(),
			rule.canSelectAfternoonLecture()
		);
		Map<String, Object> response = new java.util.LinkedHashMap<>();

		addLectureAvailabilities(response, lectureSelection.morningLectures());
		addLectureAvailabilities(response, lectureSelection.afternoonLectures());

		return response;
	}

	private RecruitmentEntity findLectureFlowRecruitment(HttpSession session) {
		if (participantSession.isLectureEditMode(session)) {
			Long participantLectureId = participantSession.participantLectureId(session);

			if (participantLectureId == null) {
				return null;
			}

			return participantRegistrationViewService.findRecruitmentForParticipantLecture(participantLectureId);
		}

		Long registrationRecruitmentId = participantSession.registrationRecruitmentId(session);

		if (registrationRecruitmentId != null) {
			return recruitmentQueryService.findRecruitment(registrationRecruitmentId);
		}

		return recruitmentQueryService.findCurrentRecruitment()
			.orElse(null);
	}

	private RecruitmentEntity requireLectureFlowRecruitment(HttpSession session) {
		RecruitmentEntity recruitmentEntity = findLectureFlowRecruitment(session);

		if (recruitmentEntity == null) {
			throw new IllegalStateException("신청 중인 모집 정보를 찾을 수 없습니다. 다시 시작해주세요.");
		}

		return recruitmentEntity;
	}

	private void addLectureAvailabilities(
		Map<String, Object> response,
		java.util.List<LectureQueryService.LectureView> lectures
	) {
		lectures.forEach(lecture -> response.put(
			String.valueOf(lecture.id()),
			Map.of(
				"remainingCapacity", lecture.getRemainingCapacity(),
				"full", lecture.isFull(),
				"open", lecture.isOpen()
			)
		));
	}

	private Map<String, Object> toSelectedLectureResponse(
		Map<LectureType, ParticipantLectureDraftStatus.SelectedLecture> selectedLectures
	) {
		Map<String, Object> response = new java.util.LinkedHashMap<>();

		selectedLectures.forEach((lectureType, selectedLecture) -> response.put(
			lectureType.name(),
			Map.of(
				"lectureId", selectedLecture.lectureId(),
				"remainingCapacity", selectedLecture.remainingCapacity(),
				"expiresAt", selectedLecture.expiresAt()
			)
		));

		return response;
	}

	private boolean isAdmin(HttpSession session) {
		return session.getAttribute(ADMIN_ID_SESSION_KEY) != null;
	}

	private void cancelPendingExitRelease(HttpSession session) {
		draftExitReleaseService.cancelRelease(participantSession.draftToken(session));
	}

	private ResponseEntity<Map<String, Object>> toFailureResponse(RuntimeException exception) {
		boolean retryable = exception instanceof TransientDataAccessException
			|| exception instanceof DataIntegrityViolationException;
		Map<String, Object> body = new java.util.LinkedHashMap<>();

		body.put("success", false);
		body.put("message", getClientMessage(exception));
		body.put("retryable", retryable);

		return ResponseEntity.status(retryable ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST).body(body);
	}

	private String getClientMessage(RuntimeException exception) {
		if (exception instanceof IllegalArgumentException || exception instanceof IllegalStateException) {
			String message = exception.getMessage();

			if (message != null && !message.isBlank()) {
				return message;
			}
		}

		if (exception instanceof TransientDataAccessException
			|| exception instanceof DataIntegrityViolationException) {
			return "요청이 몰리고 있습니다. 현재 상태를 확인한 뒤 다시 시도해주세요.";
		}

		return "요청을 처리하지 못했습니다. 현재 상태를 확인한 뒤 다시 시도해주세요.";
	}

}
