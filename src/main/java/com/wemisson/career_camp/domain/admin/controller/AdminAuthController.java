package com.wemisson.career_camp.domain.admin.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.wemisson.career_camp.domain.admin.service.auth.AdminAuthService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class AdminAuthController {

	public static final String ADMIN_ID_SESSION_KEY = "adminId";
	public static final String ADMIN_NAME_SESSION_KEY = "adminName";

	private final AdminAuthService adminAuthService;

	@Value("${career-camp.admin.session-timeout-seconds:-1}")
	private int adminSessionTimeoutSeconds;

	@GetMapping("/admin/login")
	public String adminLogin() {
		return "redirect:/home";
	}

	@PostMapping("/admin/login")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> login(
		@RequestParam String name,
		@RequestParam String password,
		HttpSession session
	) {
		AdminAuthService.AuthenticatedAdmin admin = adminAuthService.authenticate(name, password)
			.orElse(null);

		if (admin == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of(
					"success", false,
					"message", "아이디 또는 비밀번호가 올바르지 않습니다."
				));
		}

		session.setAttribute(ADMIN_ID_SESSION_KEY, admin.id());
		session.setAttribute(ADMIN_NAME_SESSION_KEY, admin.name());
		session.setMaxInactiveInterval(adminSessionTimeoutSeconds);

		return ResponseEntity.ok(Map.of(
			"success", true,
			"redirectUrl", "/admin/home"
		));
	}

	@PostMapping("/admin/logout")
	public String logout(HttpSession session) {
		session.removeAttribute(ADMIN_ID_SESSION_KEY);
		session.removeAttribute(ADMIN_NAME_SESSION_KEY);

		return "redirect:/home";
	}
}
