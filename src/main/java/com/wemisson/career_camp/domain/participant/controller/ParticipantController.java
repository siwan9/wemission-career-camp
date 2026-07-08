package com.wemisson.career_camp.domain.participant.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.wemisson.career_camp.domain.participant.dto.ParticipantCreateRequest;
import com.wemisson.career_camp.domain.participant.service.RegistrationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ParticipantController {

	private final RegistrationService registrationService;

	@PostMapping("/registrations")
	public String register(
		@Valid @ModelAttribute ParticipantCreateRequest request,
		RedirectAttributes redirectAttributes,
		Model model
	) {

		try {
			registrationService.register(request);
			redirectAttributes.addFlashAttribute("success",
				"successfully registered");

			return "redirect:/home";

		} catch (Exception e) {

			model.addAttribute(
				"errorMessage",
				""
			);

			model.addAttribute(
				"request",
				request
			);

			return "register";
		}
	}
}
