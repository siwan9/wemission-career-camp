package com.wemisson.career_camp.domain.participant.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.wemisson.career_camp.domain.participant.dto.ParticipantCreateRequest;
import com.wemisson.career_camp.domain.participant.service.command.LectureApplicationService;
import com.wemisson.career_camp.domain.participant.service.query.ParticipantRegistrationViewService;
import com.wemisson.career_camp.domain.participant.session.ParticipantSession;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;
import com.wemisson.career_camp.domain.recruitment.service.query.RecruitmentQueryService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ParticipantRegistrationController {

	private final RecruitmentQueryService recruitmentService;
	private final ParticipantRegistrationViewService participantRegistrationViewService;
	private final LectureApplicationService lectureApplicationService;
	private final ParticipantSession participantSession;

	@PostMapping("/lecture")
	public String saveRegistrationToSession(
		@Valid @ModelAttribute("request") ParticipantCreateRequest request,
		BindingResult bindingResult,
		Model model,
		HttpSession session
	) {
		RecruitmentEntity recruitmentEntity = findRegistrationFlowRecruitment(session);

		if (recruitmentEntity == null) {
			return "redirect:/home";
		}

		if (bindingResult.hasErrors()) {
			addRegisterAttributes(model, false, recruitmentEntity);
			return "register";
		}

		RegistrationContext registrationContext = createRegistrationContext(
			recruitmentEntity,
			request
		);

		if (!registrationContext.canSelectMorningLecture() && !registrationContext.canSelectAfternoonLecture()) {
			participantSession.clearRegistration(session);

			return "redirect:/register";
		}

		participantSession.startNewRegistration(
			session,
			request,
			participantSession.registrationRecruitmentId(session)
		);

		return "redirect:/lecture";
	}

	@PostMapping("/registration/cancel")
	public String cancelRegistration(HttpSession session) {
		lectureApplicationService.releaseDrafts(participantSession.draftToken(session));
		participantSession.clearRegistration(session);

		String adminReturnUrl = participantSession.consumeAdminReturnUrl(session);
		if (adminReturnUrl != null) {
			return "redirect:" + adminReturnUrl;
		}

		return "redirect:/home";
	}

	@PostMapping("/registration/back-to-register")
	public String backToRegister(HttpSession session) {
		lectureApplicationService.releaseDrafts(participantSession.draftToken(session));

		return "redirect:/register";
	}

	@PostMapping("/register/edit")
	public String updateParticipant(
		@Valid @ModelAttribute("request") ParticipantCreateRequest request,
		BindingResult bindingResult,
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

		if (bindingResult.hasErrors()) {
			addRegisterAttributes(model, true, editView.recruitment());
			model.addAttribute("adminReturnUrl", participantSession.adminReturnUrl(session));

			return "register";
		}

		participantRegistrationViewService.updateParticipant(editingParticipantId, request);
		participantSession.finishParticipantEdit(session, request);

		String adminReturnUrl = participantSession.consumeAdminReturnUrl(session);
		if (adminReturnUrl != null) {
			return "redirect:" + adminReturnUrl;
		}

		if (participantSession.consumeReturnToLookupAfterEdit(session)) {
			return "redirect:/lookup";
		}

		return "redirect:/lecture";
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

	private RecruitmentEntity findRegistrationFlowRecruitment(HttpSession session) {
		Long registrationRecruitmentId = participantSession.registrationRecruitmentId(session);

		if (registrationRecruitmentId != null) {
			return recruitmentService.findRecruitment(registrationRecruitmentId);
		}

		return recruitmentService.findCurrentRecruitment()
			.orElse(null);
	}

	private void addRegisterAttributes(
		Model model,
		boolean editMode,
		RecruitmentEntity recruitmentEntity
	) {
		List<?> participantTypes = participantRegistrationViewService.findSelectableParticipantTypes(recruitmentEntity);
		List<?> churches = participantRegistrationViewService.findSelectableChurches(recruitmentEntity);

		model.addAttribute("editMode", editMode);
		model.addAttribute("recruitmentNoticeLines", recruitmentService.findNoticeLines(recruitmentEntity));
		model.addAttribute("participantTypes", participantTypes);
		model.addAttribute("churches", churches);
		model.addAttribute("canSubmitRegistration", !participantTypes.isEmpty() && !churches.isEmpty());
	}

	private record RegistrationContext(
		boolean canSelectMorningLecture,
		boolean canSelectAfternoonLecture
	) {
	}
}
