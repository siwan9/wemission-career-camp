package com.wemisson.career_camp.domain.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;

import com.wemisson.career_camp.domain.admin.service.auth.AdminAuthService;

class AdminAuthControllerTest {

	@Test
	void 관리자_로그인에_성공하면_세션이_자동으로_만료되지_않는다() {
		AdminAuthService adminAuthService = mock(AdminAuthService.class);
		when(adminAuthService.authenticate("admin", "password"))
			.thenReturn(Optional.of(new AdminAuthService.AuthenticatedAdmin(1L, "admin")));
		AdminAuthController controller = new AdminAuthController(adminAuthService);
		ReflectionTestUtils.setField(controller, "adminSessionTimeoutSeconds", -1);
		MockHttpSession session = new MockHttpSession();

		ResponseEntity<?> response = controller.login("admin", "password", session);

		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(session.getAttribute(AdminAuthController.ADMIN_ID_SESSION_KEY)).isEqualTo(1L);
		assertThat(session.getMaxInactiveInterval()).isEqualTo(-1);
	}
}
