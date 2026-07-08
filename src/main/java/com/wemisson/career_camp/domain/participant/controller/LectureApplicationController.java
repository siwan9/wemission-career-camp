package com.wemisson.career_camp.domain.participant.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.wemisson.career_camp.domain.participant.dto.LectureApplicationResult;
import com.wemisson.career_camp.domain.participant.dto.ParticipantCreateRequest;
import com.wemisson.career_camp.domain.participant.service.LectureApplicationService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class LectureApplicationController {

	private static final String REGISTRATION_REQUEST_SESSION_KEY = "registrationRequest";
	private static final String PARTICIPANT_LECTURE_ID_SESSION_KEY = "participantLectureId";

	private final LectureApplicationService lectureApplicationService;

	@PostMapping("/lectures/apply")
	public String apply(
		@RequestParam Long lectureId,
		HttpSession session,
		RedirectAttributes redirectAttributes
	) {
		ParticipantCreateRequest request = (ParticipantCreateRequest)session.getAttribute(
			REGISTRATION_REQUEST_SESSION_KEY
		);

		if (request == null) {
			return "redirect:/register";
		}

		try {
			LectureApplicationResult result = lectureApplicationService.apply(
				request,
				(Long)session.getAttribute(PARTICIPANT_LECTURE_ID_SESSION_KEY),
				lectureId
			);

			session.setAttribute(
				PARTICIPANT_LECTURE_ID_SESSION_KEY,
				result.participantLectureId()
			);
			redirectAttributes.addFlashAttribute("successMessage", "특강 신청이 저장되었습니다.");
		} catch (RuntimeException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}

		return "redirect:/lecture";
	}

	@PostMapping(value = "/lectures/apply", headers = "X-Requested-With=XMLHttpRequest")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> applyAjax(
		@RequestParam Long lectureId,
		HttpSession session
	) {
		ParticipantCreateRequest request = (ParticipantCreateRequest)session.getAttribute(
			REGISTRATION_REQUEST_SESSION_KEY
		);

		if (request == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("success", false, "message", "참가자 정보를 먼저 입력해주세요."));
		}

		try {
			LectureApplicationResult result = lectureApplicationService.apply(
				request,
				(Long)session.getAttribute(PARTICIPANT_LECTURE_ID_SESSION_KEY),
				lectureId
			);

			session.setAttribute(
				PARTICIPANT_LECTURE_ID_SESSION_KEY,
				result.participantLectureId()
			);

			return ResponseEntity.ok(toResponse(result, true, "특강 신청이 저장되었습니다."));
		} catch (RuntimeException e) {
			return ResponseEntity.badRequest()
				.body(Map.of("success", false, "message", e.getMessage()));
		}
	}

	@PostMapping("/lectures/cancel")
	public String cancel(
		@RequestParam Long lectureId,
		HttpSession session,
		RedirectAttributes redirectAttributes
	) {
		try {
			lectureApplicationService.cancel(
				(Long)session.getAttribute(PARTICIPANT_LECTURE_ID_SESSION_KEY),
				lectureId
			);
			redirectAttributes.addFlashAttribute("successMessage", "특강 신청이 취소되었습니다.");
		} catch (RuntimeException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}

		return "redirect:/lecture";
	}

	@PostMapping(value = "/lectures/cancel", headers = "X-Requested-With=XMLHttpRequest")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> cancelAjax(
		@RequestParam Long lectureId,
		HttpSession session
	) {
		try {
			LectureApplicationResult result = lectureApplicationService.cancel(
				(Long)session.getAttribute(PARTICIPANT_LECTURE_ID_SESSION_KEY),
				lectureId
			);

			return ResponseEntity.ok(toResponse(result, false, "특강 신청이 취소되었습니다."));
		} catch (RuntimeException e) {
			return ResponseEntity.badRequest()
				.body(Map.of("success", false, "message", e.getMessage()));
		}
	}

	private Map<String, Object> toResponse(
		LectureApplicationResult result,
		boolean selected,
		String message
	) {
		return Map.of(
			"success", true,
			"message", message,
			"selected", selected,
			"lectureId", result.lectureId(),
			"lectureType", result.lectureType().name(),
			"remainingCapacity", result.remainingCapacity()
		);
	}
}
