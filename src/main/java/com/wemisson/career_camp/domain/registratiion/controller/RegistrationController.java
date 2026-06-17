package com.wemisson.career_camp.domain.registratiion.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.wemisson.career_camp.domain.registratiion.dto.RegistrationRequest;
import com.wemisson.career_camp.domain.registratiion.service.RegistrationService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class RegistrationController {

	private final RegistrationService registrationService;

	@PostMapping("/registrations")
	public String register(
		@ModelAttribute RegistrationRequest request,
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
