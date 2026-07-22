package com.wemisson.career_camp.domain.admin.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.wemisson.career_camp.domain.admin.dto.AdminChurchRequest;
import com.wemisson.career_camp.domain.admin.dto.AdminLectureRequest;
import com.wemisson.career_camp.domain.admin.dto.AdminParticipantTypeRuleRequest;
import com.wemisson.career_camp.domain.admin.dto.AdminRecruitmentRequest;
import com.wemisson.career_camp.domain.admin.service.command.AdminRecruitmentCommandService;
import com.wemisson.career_camp.domain.admin.service.export.RecruitmentExcelExporter;
import com.wemisson.career_camp.domain.participant.session.ParticipantSession;
import com.wemisson.career_camp.domain.recruitment.dto.RecruitmentStatus;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class AdminRecruitmentController {

	private final ParticipantSession participantSession;
	private final AdminRecruitmentCommandService adminRecruitmentCommandService;
	private final RecruitmentExcelExporter recruitmentExcelExporter;

	@GetMapping("/admin/recruitments/{recruitmentId}/excel")
	public ResponseEntity<byte[]> downloadExcel(
		@PathVariable Long recruitmentId,
		@RequestParam(required = false, defaultValue = "lecture") String downloadType
	) throws IOException {
		RecruitmentExcelExporter.ExportedRecruitmentExcel excel = recruitmentExcelExporter.export(
			recruitmentId,
			downloadType
		);
		String reportName = "church".equals(downloadType) ? "교회별현황" : "강좌별현황";
		String filename = sanitizeFilename(excel.recruitmentName()) + "_" + reportName + ".xlsx";

		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
				.filename(filename, StandardCharsets.UTF_8)
				.build()
				.toString())
			.body(excel.content());
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/participants/{participantId}/delete")
	public String deleteParticipant(
		@PathVariable Long recruitmentId,
		@PathVariable Long participantId,
		@RequestParam(required = false) String returnUrl
	) {
		adminRecruitmentCommandService.deleteParticipant(recruitmentId, participantId);

		return "redirect:" + getAdminReturnUrl(recruitmentId, returnUrl);
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/participants/new")
	public String newParticipant(
		@PathVariable Long recruitmentId,
		HttpSession session,
		@RequestParam(required = false) String returnUrl
	) {
		Long verifiedRecruitmentId = adminRecruitmentCommandService.findRecruitmentId(recruitmentId);

		participantSession.startAdminRegistration(session, verifiedRecruitmentId);
		participantSession.setAdminReturnUrl(session, getAdminReturnUrl(recruitmentId, returnUrl));

		return "redirect:/register";
	}

	@PostMapping("/admin/recruitments")
	public String createRecruitment(
		@ModelAttribute AdminRecruitmentRequest request,
		RedirectAttributes redirectAttributes
	) {
		adminRecruitmentCommandService.createRecruitment(
			request.name(),
			request.description(),
			request.notice(),
			request.startAt(),
			request.endAt()
		);
		redirectAttributes.addFlashAttribute("successMessage", "모집이 추가되었습니다.");

		return "redirect:/admin/home";
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/delete")
	public String deleteRecruitment(
		@PathVariable Long recruitmentId,
		RedirectAttributes redirectAttributes
	) {
		try {
			adminRecruitmentCommandService.deleteRecruitment(recruitmentId);
		} catch (IllegalArgumentException | IllegalStateException e) {
			redirectAttributes.addFlashAttribute("operationErrorMessage", e.getMessage());

			return "redirect:/admin/home";
		}

		redirectAttributes.addFlashAttribute("successMessage", "모집이 삭제되었습니다.");

		return "redirect:/admin/home";
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/settings")
	public String updateRecruitment(
		@PathVariable Long recruitmentId,
		@ModelAttribute AdminRecruitmentRequest request,
		RedirectAttributes redirectAttributes
	) {
		try {
			adminRecruitmentCommandService.updateRecruitment(
				recruitmentId,
				request.name(),
				request.description(),
				request.notice(),
				request.startAt(),
				request.endAt(),
				request.status()
			);
		} catch (IllegalStateException e) {
			redirectAttributes.addFlashAttribute("toastMessage", e.getMessage());
			return "redirect:/admin/recruitments/" + recruitmentId + "/settings";
		}

		redirectAttributes.addFlashAttribute("toastMessage", "모집 정보가 저장되었습니다.");

		return "redirect:/admin/recruitments/" + recruitmentId + "/settings";
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/toggle-open")
	public String toggleRecruitmentOpen(
		@PathVariable Long recruitmentId,
		RedirectAttributes redirectAttributes
	) {
		RecruitmentStatus nextStatus;

		try {
			nextStatus = adminRecruitmentCommandService.toggleRecruitmentStatus(recruitmentId);
		} catch (IllegalStateException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());

			return "redirect:/admin/recruitment-dashboard/" + recruitmentId;
		}

		redirectAttributes.addFlashAttribute(
			"toastMessage",
			"모집 상태가 " + nextStatus.getDescription() + "(으)로 변경되었습니다."
		);

		return "redirect:/admin/recruitment-dashboard/" + recruitmentId;
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/status")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> changeRecruitmentStatus(
		@PathVariable Long recruitmentId,
		@RequestParam RecruitmentStatus status
	) {
		try {
			RecruitmentStatus changedStatus = adminRecruitmentCommandService.changeRecruitmentStatus(
				recruitmentId,
				status
			);

			return ResponseEntity.ok(Map.of(
				"success", true,
				"status", changedStatus.name(),
				"description", changedStatus.getDescription()
			));
		} catch (IllegalArgumentException | IllegalStateException e) {
			return ResponseEntity.badRequest()
				.body(Map.of(
					"success", false,
					"message", e.getMessage()
				));
		}
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/participant-types/{ruleId}")
	public String updateParticipantTypeRule(
		@PathVariable Long recruitmentId,
		@PathVariable Long ruleId,
		@ModelAttribute AdminParticipantTypeRuleRequest request,
		RedirectAttributes redirectAttributes
	) {
		try {
			adminRecruitmentCommandService.updateParticipantTypeRule(
				recruitmentId,
				ruleId,
				request.morningLectureSelectable(),
				request.afternoonLectureSelectable(),
				request.fixedMorningLectureId(),
				request.fixedAfternoonLectureId()
			);
		} catch (IllegalArgumentException | IllegalStateException e) {
			redirectAttributes.addFlashAttribute("toastMessage", e.getMessage());
			return "redirect:/admin/recruitments/" + recruitmentId + "/settings";
		}
		redirectAttributes.addFlashAttribute("toastMessage", "참가자 타입 설정이 저장되었습니다.");

		return "redirect:/admin/recruitments/" + recruitmentId + "/settings";
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/lectures/{lectureId}")
	public String updateLecture(
		@PathVariable Long recruitmentId,
		@PathVariable Long lectureId,
		@ModelAttribute AdminLectureRequest request,
		RedirectAttributes redirectAttributes
	) {
		try {
			adminRecruitmentCommandService.updateLecture(
				recruitmentId,
				lectureId,
				request.speakerName(),
				request.speakerJob(),
				request.description(),
				request.type(),
				request.open(),
				request.maxCapacity()
			);
		} catch (IllegalArgumentException | IllegalStateException e) {
			redirectAttributes.addFlashAttribute("toastMessage", e.getMessage());
			return "redirect:/admin/recruitments/" + recruitmentId + "/settings";
		}

		redirectAttributes.addFlashAttribute("toastMessage", "강의 정보가 저장되었습니다.");

		return "redirect:/admin/recruitments/" + recruitmentId + "/settings";
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/lectures")
	public String createLecture(
		@PathVariable Long recruitmentId,
		@ModelAttribute AdminLectureRequest request,
		RedirectAttributes redirectAttributes
	) {
		adminRecruitmentCommandService.createLecture(
			recruitmentId,
			request.speakerName(),
			request.speakerJob(),
			request.description(),
			request.type(),
			request.open(),
			request.maxCapacity()
		);
		redirectAttributes.addFlashAttribute("toastMessage", "강의가 추가되었습니다.");

		return "redirect:/admin/recruitments/" + recruitmentId + "/settings";
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/lectures/{lectureId}/delete")
	public String deleteLecture(
		@PathVariable Long recruitmentId,
		@PathVariable Long lectureId,
		RedirectAttributes redirectAttributes
	) {
		adminRecruitmentCommandService.deleteLecture(recruitmentId, lectureId);
		redirectAttributes.addFlashAttribute("toastMessage", "강의가 삭제되었습니다.");

		return "redirect:/admin/recruitments/" + recruitmentId + "/settings";
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/lectures/reorder")
	@ResponseBody
	public String reorderLectures(
		@PathVariable Long recruitmentId,
		@RequestParam List<Long> lectureIds
	) {
		adminRecruitmentCommandService.reorderLectures(recruitmentId, lectureIds);

		return "ok";
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/churches")
	public String createChurch(
		@PathVariable Long recruitmentId,
		@ModelAttribute AdminChurchRequest request,
		RedirectAttributes redirectAttributes
	) {
		adminRecruitmentCommandService.createChurch(recruitmentId, request.name());
		redirectAttributes.addFlashAttribute("toastMessage", "교회가 추가되었습니다.");

		return "redirect:/admin/recruitments/" + recruitmentId + "/settings";
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/churches/reorder")
	@ResponseBody
	public String reorderChurches(
		@PathVariable Long recruitmentId,
		@RequestParam List<Long> churchIds
	) {
		adminRecruitmentCommandService.reorderChurches(recruitmentId, churchIds);

		return "ok";
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/churches/{churchId}")
	public String updateChurch(
		@PathVariable Long recruitmentId,
		@PathVariable Long churchId,
		@ModelAttribute AdminChurchRequest request,
		RedirectAttributes redirectAttributes
	) {
		adminRecruitmentCommandService.updateChurch(recruitmentId, churchId, request.name(), request.sortOrder());
		redirectAttributes.addFlashAttribute("toastMessage", "교회 정보가 저장되었습니다.");

		return "redirect:/admin/recruitments/" + recruitmentId + "/settings";
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/churches/{churchId}/delete")
	public String deleteChurch(
		@PathVariable Long recruitmentId,
		@PathVariable Long churchId,
		RedirectAttributes redirectAttributes
	) {
		adminRecruitmentCommandService.deleteChurch(recruitmentId, churchId);
		redirectAttributes.addFlashAttribute("toastMessage", "교회가 삭제되었습니다.");

		return "redirect:/admin/recruitments/" + recruitmentId + "/settings";
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/participants/{participantId}/edit-info")
	public String editParticipantInfo(
		@PathVariable Long recruitmentId,
		@PathVariable Long participantId,
		HttpSession session,
		@RequestParam(required = false) String returnUrl
	) {
		Long verifiedParticipantId = adminRecruitmentCommandService.findParticipantId(recruitmentId, participantId);

		participantSession.startParticipantEdit(session, verifiedParticipantId, null, false);
		participantSession.setAdminReturnUrl(session, getAdminReturnUrl(recruitmentId, returnUrl));

		return "redirect:/register/edit";
	}

	@PostMapping("/admin/recruitments/{recruitmentId}/participants/{participantId}/edit-lecture")
	public String editParticipantLecture(
		@PathVariable Long recruitmentId,
		@PathVariable Long participantId,
		HttpSession session,
		@RequestParam(required = false) String returnUrl
	) {
		AdminRecruitmentCommandService.LectureEditSession lectureEditSession =
			adminRecruitmentCommandService.findOrCreateLectureEditSession(recruitmentId, participantId);

		participantSession.startLectureEdit(
			session,
			lectureEditSession.request(),
			lectureEditSession.participantLectureId()
		);
		participantSession.setAdminReturnUrl(session, getAdminReturnUrl(recruitmentId, returnUrl));

		return "redirect:/lecture";
	}

	private String getAdminReturnUrl(
		Long recruitmentId,
		String returnUrl
	) {
		if (returnUrl != null && returnUrl.startsWith("/admin/")) {
			return returnUrl;
		}

		return "/admin/recruitment-dashboard/" + recruitmentId;
	}

	private String sanitizeFilename(String filename) {
		return filename.replaceAll("[\\\\/:*?\"<>|]", "_");
	}

}
