package com.wemisson.career_camp.domain.participant.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
	private static final String LOOKUP_PARTICIPANT_LECTURE_IDS_SESSION_KEY = "lookupParticipantLectureIds";

	private final ParticipantLookupService participantLookupService;

	@PostMapping("/lookup")
	public String lookup(
		@RequestParam String phoneNumber,
		@RequestParam String password,
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

		session.setAttribute(
			EDITING_PARTICIPANT_ID_SESSION_KEY,
			participantLectureEntity.getParticipantEntity().getId()
		);
		session.setAttribute(PARTICIPANT_LECTURE_ID_SESSION_KEY, participantLectureId);

		return "redirect:/register/edit";
	}
}
