package com.wemisson.career_camp.domain.participant.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.wemisson.career_camp.domain.participant.dto.ParticipantLookupResult;
import com.wemisson.career_camp.domain.participant.entity.ParticipantLectureEntity;
import com.wemisson.career_camp.domain.participant.service.ParticipantLookupService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ParticipantLookupController {

	private static final String REGISTRATION_REQUEST_SESSION_KEY = "registrationRequest";
	private static final String PARTICIPANT_LECTURE_ID_SESSION_KEY = "participantLectureId";
	private static final String EDITING_PARTICIPANT_ID_SESSION_KEY = "editingParticipantId";
	private static final String RETURN_TO_LOOKUP_AFTER_EDIT_SESSION_KEY = "returnToLookupAfterEdit";
	private static final String LOOKUP_PARTICIPANT_LECTURE_IDS_SESSION_KEY = "lookupParticipantLectureIds";
	private static final String LOOKUP_PHONE_NUMBER_SESSION_KEY = "lookupPhoneNumber";
	private static final String LOOKUP_PASSWORD_SESSION_KEY = "lookupPassword";

	private final ParticipantLookupService participantLookupService;

	@GetMapping("/lookup")
	public String lookupPage(
		Model model,
		HttpSession session
	) {
		String phoneNumber = (String)session.getAttribute(LOOKUP_PHONE_NUMBER_SESSION_KEY);
		String password = (String)session.getAttribute(LOOKUP_PASSWORD_SESSION_KEY);

		if (phoneNumber == null || password == null) {
			return "lookup";
		}

		return addLookupResults(phoneNumber, password, model, session);
	}

	@PostMapping("/lookup")
	public String lookup(
		@RequestParam String phoneNumber,
		@RequestParam String password,
		Model model,
		HttpSession session
	) {
		session.setAttribute(LOOKUP_PHONE_NUMBER_SESSION_KEY, phoneNumber);
		session.setAttribute(LOOKUP_PASSWORD_SESSION_KEY, password);

		return addLookupResults(phoneNumber, password, model, session);
	}

	private String addLookupResults(
		String phoneNumber,
		String password,
		Model model,
		HttpSession session
	) {
		if (!phoneNumber.matches("\\d{10,11}") || !password.matches("\\d{6}")) {
			model.addAttribute("phoneNumber", phoneNumber);
			model.addAttribute("password", password);
			model.addAttribute("searched", true);
			model.addAttribute("results", List.of());
			model.addAttribute(
				"lookupErrorMessage",
				"전화번호는 10~11자리 숫자, PIN은 6자리 숫자로 입력해주세요."
			);

			return "lookup";
		}

		List<ParticipantLookupResult> results = participantLookupService.lookup(
			phoneNumber,
			password
		);

		model.addAttribute("phoneNumber", phoneNumber);
		model.addAttribute("password", password);
		model.addAttribute("results", results);
		model.addAttribute("searched", true);
		session.setAttribute(
			LOOKUP_PARTICIPANT_LECTURE_IDS_SESSION_KEY,
			results.stream()
				.map(ParticipantLookupResult::participantLectureId)
				.toList()
		);

		return "lookup";
	}

	@PostMapping("/lookup/edit-lecture")
	public String editLecture(
		@RequestParam Long participantLectureId,
		HttpSession session,
		RedirectAttributes redirectAttributes
	) {
		List<Long> lookupParticipantLectureIds = (List<Long>)session.getAttribute(
			LOOKUP_PARTICIPANT_LECTURE_IDS_SESSION_KEY
		);

		if (
			lookupParticipantLectureIds == null
				|| !lookupParticipantLectureIds.contains(participantLectureId)
		) {
			redirectAttributes.addFlashAttribute("errorMessage", "다시 조회 후 수정해주세요.");

			return "redirect:/lookup";
		}

		ParticipantLectureEntity participantLectureEntity = participantLookupService.findParticipantLecture(
			participantLectureId
		);

		if (!participantLectureEntity.getParticipantEntity().getRecruitmentEntity().isOpen()) {
			redirectAttributes.addFlashAttribute("lookupErrorMessage", "모집이 종료되어 신청 정보를 수정할 수 없습니다.");

			return "redirect:/lookup";
		}

		session.setAttribute(
			REGISTRATION_REQUEST_SESSION_KEY,
			participantLookupService.toCreateRequest(participantLectureEntity)
		);
		session.setAttribute(PARTICIPANT_LECTURE_ID_SESSION_KEY, participantLectureId);

		return "redirect:/lecture";
	}

	@PostMapping("/lookup/edit-participant")
	public String editParticipant(
		@RequestParam Long participantLectureId,
		HttpSession session,
		RedirectAttributes redirectAttributes
	) {
		List<Long> lookupParticipantLectureIds = (List<Long>)session.getAttribute(
			LOOKUP_PARTICIPANT_LECTURE_IDS_SESSION_KEY
		);

		if (
			lookupParticipantLectureIds == null
				|| !lookupParticipantLectureIds.contains(participantLectureId)
		) {
			redirectAttributes.addFlashAttribute("errorMessage", "다시 조회 후 수정해주세요.");

			return "redirect:/lookup";
		}

		ParticipantLectureEntity participantLectureEntity = participantLookupService.findParticipantLecture(
			participantLectureId
		);

		if (!participantLectureEntity.getParticipantEntity().getRecruitmentEntity().isOpen()) {
			redirectAttributes.addFlashAttribute("lookupErrorMessage", "모집이 종료되어 신청 정보를 수정할 수 없습니다.");

			return "redirect:/lookup";
		}

		session.setAttribute(
			EDITING_PARTICIPANT_ID_SESSION_KEY,
			participantLectureEntity.getParticipantEntity().getId()
		);
		session.setAttribute(RETURN_TO_LOOKUP_AFTER_EDIT_SESSION_KEY, true);
		session.setAttribute(PARTICIPANT_LECTURE_ID_SESSION_KEY, participantLectureId);

		return "redirect:/register/edit";
	}
}
