package com.wemisson.career_camp.domain.participant.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.wemisson.career_camp.domain.participant.session.ParticipantSession;
import com.wemisson.career_camp.domain.participant.service.command.ParticipantLookupService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ParticipantLookupController {

	private final ParticipantLookupService participantLookupService;
	private final ParticipantSession participantSession;

	@PostMapping("/lookup/edit-lecture")
	public String editLecture(
		@RequestParam Long participantLectureId,
		HttpSession session,
		RedirectAttributes redirectAttributes
	) {
		if (!participantSession.canAccessLookupResult(session, participantLectureId)) {
			redirectAttributes.addFlashAttribute("errorMessage", "다시 조회 후 수정해주세요.");

			return "redirect:/lookup";
		}

		ParticipantLookupService.LookupEditTarget editTarget = participantLookupService.findLookupEditTarget(
			participantLectureId
		);

		if (!editTarget.recruitmentOpen()) {
			redirectAttributes.addFlashAttribute("lookupErrorMessage", "모집이 종료되어 신청 정보를 수정할 수 없습니다.");

			return "redirect:/lookup";
		}

		participantSession.startLectureEdit(
			session,
			editTarget.request(),
			editTarget.participantLectureId()
		);

		return "redirect:/lecture";
	}

	@PostMapping("/lookup/edit-participant")
	public String editParticipant(
		@RequestParam Long participantLectureId,
		HttpSession session,
		RedirectAttributes redirectAttributes
	) {
		if (!participantSession.canAccessLookupResult(session, participantLectureId)) {
			redirectAttributes.addFlashAttribute("errorMessage", "다시 조회 후 수정해주세요.");

			return "redirect:/lookup";
		}

		ParticipantLookupService.LookupEditTarget editTarget = participantLookupService.findLookupEditTarget(
			participantLectureId
		);

		if (!editTarget.recruitmentOpen()) {
			redirectAttributes.addFlashAttribute("lookupErrorMessage", "모집이 종료되어 신청 정보를 수정할 수 없습니다.");

			return "redirect:/lookup";
		}

		participantSession.startParticipantEdit(
			session,
			editTarget.participantId(),
			editTarget.participantLectureId(),
			true
		);

		return "redirect:/register/edit";
	}

	@PostMapping("/lookup/delete")
	public String deleteParticipantLecture(
		@RequestParam Long participantLectureId,
		HttpSession session,
		RedirectAttributes redirectAttributes
	) {
		if (!participantSession.canAccessLookupResult(session, participantLectureId)) {
			redirectAttributes.addFlashAttribute("errorMessage", "다시 조회 후 삭제해주세요.");

			return "redirect:/lookup";
		}

		ParticipantLookupService.LookupEditTarget editTarget = participantLookupService.findLookupEditTarget(
			participantLectureId
		);

		if (!editTarget.recruitmentOpen()) {
			redirectAttributes.addFlashAttribute("lookupErrorMessage", "모집이 종료되어 신청 정보를 삭제할 수 없습니다.");

			return "redirect:/lookup";
		}

		participantLookupService.deleteParticipantLecture(participantLectureId);
		redirectAttributes.addFlashAttribute("lookupSuccessMessage", "신청 정보가 삭제되었습니다.");

		return "redirect:/lookup";
	}
}
