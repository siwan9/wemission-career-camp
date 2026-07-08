package com.wemisson.career_camp.domain.admin.config;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import static com.wemisson.career_camp.domain.admin.controller.AdminAuthController.ADMIN_ID_SESSION_KEY;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(
		HttpServletRequest request,
		HttpServletResponse response,
		Object handler
	) throws IOException {
		if (isLoginRequest(request)) {
			return true;
		}

		HttpSession session = request.getSession(false);

		if (session != null && session.getAttribute(ADMIN_ID_SESSION_KEY) != null) {
			return true;
		}

		if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.getWriter().write("{\"success\":false,\"message\":\"관리자 로그인이 필요합니다.\"}");
			return false;
		}

		response.sendRedirect("/home");
		return false;
	}

	private boolean isLoginRequest(HttpServletRequest request) {
		return "/admin/login".equals(request.getRequestURI())
			&& "POST".equalsIgnoreCase(request.getMethod());
	}
}
