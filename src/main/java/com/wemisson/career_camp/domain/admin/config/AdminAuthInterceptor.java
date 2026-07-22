package com.wemisson.career_camp.domain.admin.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import static com.wemisson.career_camp.domain.admin.controller.AdminAuthController.ADMIN_ID_SESSION_KEY;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {
	static final String LOGIN_REQUIRED_REDIRECT_URL = "/home?adminLogin=session-expired";
	static final String LOGIN_REQUIRED_MESSAGE =
		"관리자 로그인 정보가 없거나 세션이 만료되어 요청을 처리하지 못했습니다. "
			+ "입력하거나 변경한 내용은 저장되지 않았습니다. 다시 로그인한 뒤 작업을 다시 진행해주세요.";

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
			response.setCharacterEncoding(StandardCharsets.UTF_8.name());
			response.getWriter().write(
				"{\"success\":false,\"code\":\"ADMIN_SESSION_EXPIRED\",\"message\":\""
					+ LOGIN_REQUIRED_MESSAGE
					+ "\",\"redirectUrl\":\""
					+ LOGIN_REQUIRED_REDIRECT_URL
					+ "\"}"
			);
			return false;
		}

		response.sendRedirect(LOGIN_REQUIRED_REDIRECT_URL);
		return false;
	}

	private boolean isLoginRequest(HttpServletRequest request) {
		return "/admin/login".equals(request.getRequestURI());
	}
}
