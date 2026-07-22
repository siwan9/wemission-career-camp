package com.wemisson.career_camp.domain.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.wemisson.career_camp.domain.admin.service.command.AdminRecruitmentCommandService;
import com.wemisson.career_camp.domain.admin.service.export.RecruitmentExcelExporter;
import com.wemisson.career_camp.domain.participant.session.ParticipantSession;

@ExtendWith(MockitoExtension.class)
class AdminRecruitmentControllerTest {

	@Mock
	private ParticipantSession participantSession;
	@Mock
	private AdminRecruitmentCommandService adminRecruitmentCommandService;
	@Mock
	private RecruitmentExcelExporter recruitmentExcelExporter;
	@InjectMocks
	private AdminRecruitmentController controller;

	@Test
	void 모집중인_모집_삭제를_시도하면_오류_안내와_함께_관리자_홈으로_돌아간다() {
		Long recruitmentId = 1L;
		String message = "모집중 또는 대기중인 모집은 종료한 뒤 삭제해주세요.";
		RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
		doThrow(new IllegalStateException(message))
			.when(adminRecruitmentCommandService)
			.deleteRecruitment(recruitmentId);

		String viewName = controller.deleteRecruitment(recruitmentId, redirectAttributes);

		assertThat(viewName).isEqualTo("redirect:/admin/home");
		assertThat(redirectAttributes.getFlashAttributes().get("operationErrorMessage")).isEqualTo(message);
		assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKey("successMessage");
	}
}
