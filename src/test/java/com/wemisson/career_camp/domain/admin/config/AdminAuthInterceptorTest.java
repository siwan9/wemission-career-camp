package com.wemisson.career_camp.domain.admin.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminAuthInterceptorTest {

	private final AdminAuthInterceptor interceptor = new AdminAuthInterceptor();

	@Test
	void 관리자_로그인_페이지_요청은_세션_검사에서_제외한다() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/login");
		MockHttpServletResponse response = new MockHttpServletResponse();

		boolean handled = interceptor.preHandle(request, response, new Object());

		assertThat(handled).isTrue();
		assertThat(response.getRedirectedUrl()).isNull();
	}

	@Test
	void 관리자_세션이_만료된_AJAX_요청은_상세안내와_로그인주소를_반환한다() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest(
			"POST",
			"/admin/recruitments/1/lectures"
		);
		request.addHeader("X-Requested-With", "XMLHttpRequest");
		MockHttpServletResponse response = new MockHttpServletResponse();

		boolean handled = interceptor.preHandle(request, response, new Object());

		assertThat(handled).isFalse();
		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
		assertThat(response.getContentAsString()).contains(
			"ADMIN_SESSION_EXPIRED",
			AdminAuthInterceptor.LOGIN_REQUIRED_MESSAGE,
			AdminAuthInterceptor.LOGIN_REQUIRED_REDIRECT_URL
		);
	}

	@Test
	void 관리자_세션이_만료된_일반요청은_로그인창을_여는_홈으로_이동한다() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest(
			"POST",
			"/admin/recruitments"
		);
		MockHttpServletResponse response = new MockHttpServletResponse();

		boolean handled = interceptor.preHandle(request, response, new Object());

		assertThat(handled).isFalse();
		assertThat(response.getRedirectedUrl()).isEqualTo(AdminAuthInterceptor.LOGIN_REQUIRED_REDIRECT_URL);
	}
}
