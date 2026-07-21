package com.wemisson.career_camp.domain.participant.session;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.wemisson.career_camp.domain.participant.dto.ParticipantCreateRequest;

import jakarta.servlet.http.HttpSession;

@Component
public class ParticipantSession {

	private static final String REGISTRATION_FLOW_SESSION_KEY = "registrationFlow";
	private static final String LOOKUP_FLOW_SESSION_KEY = "lookupFlow";
	private static final String ADMIN_RETURN_URL_SESSION_KEY = "adminReturnUrl";

	public void clearLookup(HttpSession session) {
		session.removeAttribute(LOOKUP_FLOW_SESSION_KEY);
	}

	public void clearRegistration(HttpSession session) {
		session.removeAttribute(REGISTRATION_FLOW_SESSION_KEY);
	}

	public ParticipantCreateRequest registrationRequest(HttpSession session) {
		return registrationFlow(session).request();
	}

	public Long participantLectureId(HttpSession session) {
		return registrationFlow(session).participantLectureId();
	}

	public Long editingParticipantId(HttpSession session) {
		return registrationFlow(session).editingParticipantId();
	}

	public String draftToken(HttpSession session) {
		return registrationFlow(session).draftToken();
	}

	public boolean isLectureEditMode(HttpSession session) {
		return registrationFlow(session).lectureEditMode();
	}

	public Long registrationRecruitmentId(HttpSession session) {
		return registrationFlow(session).registrationRecruitmentId();
	}

	public void startAdminRegistration(
		HttpSession session,
		Long recruitmentId
	) {
		setRegistrationFlow(
			session,
			new RegistrationFlow(null, null, createDraftToken(), null, false, false, recruitmentId)
		);
	}

	public void startNewRegistration(
		HttpSession session,
		ParticipantCreateRequest request
	) {
		startNewRegistration(session, request, null);
	}

	public void startNewRegistration(
		HttpSession session,
		ParticipantCreateRequest request,
		Long recruitmentId
	) {
		setRegistrationFlow(
			session,
			new RegistrationFlow(request, null, createDraftToken(), null, false, false, recruitmentId)
		);
	}

	public void startLectureEdit(
		HttpSession session,
		ParticipantCreateRequest request,
		Long participantLectureId
	) {
		setRegistrationFlow(
			session,
			new RegistrationFlow(request, participantLectureId, createDraftToken(), null, false, true, null)
		);
	}

	public void startParticipantEdit(
		HttpSession session,
		Long editingParticipantId,
		Long participantLectureId,
		boolean returnToLookupAfterEdit
	) {
		RegistrationFlow flow = registrationFlow(session);

		setRegistrationFlow(
			session,
			new RegistrationFlow(
				flow.request(),
				participantLectureId,
					flow.draftToken(),
					editingParticipantId,
					returnToLookupAfterEdit,
					flow.lectureEditMode(),
					flow.registrationRecruitmentId()
				)
			);
	}

	public void finishParticipantEdit(
		HttpSession session,
		ParticipantCreateRequest request
	) {
		RegistrationFlow flow = registrationFlow(session);

		setRegistrationFlow(
			session,
			new RegistrationFlow(
				request,
				flow.participantLectureId(),
					createDraftToken(),
					null,
					flow.returnToLookupAfterEdit(),
					flow.lectureEditMode(),
					flow.registrationRecruitmentId()
				)
			);
	}

	public boolean consumeReturnToLookupAfterEdit(HttpSession session) {
		RegistrationFlow flow = registrationFlow(session);

		if (!flow.returnToLookupAfterEdit()) {
			return false;
		}

		setRegistrationFlow(
			session,
			new RegistrationFlow(
				flow.request(),
				flow.participantLectureId(),
					flow.draftToken(),
					flow.editingParticipantId(),
					false,
					flow.lectureEditMode(),
					flow.registrationRecruitmentId()
				)
			);

		return true;
	}

	public String getOrCreateDraftToken(HttpSession session) {
		RegistrationFlow flow = registrationFlow(session);

		if (flow.draftToken() != null) {
			return flow.draftToken();
		}

		String draftToken = createDraftToken();
		setRegistrationFlow(
			session,
			new RegistrationFlow(
				flow.request(),
				flow.participantLectureId(),
					draftToken,
					flow.editingParticipantId(),
					flow.returnToLookupAfterEdit(),
					flow.lectureEditMode(),
					flow.registrationRecruitmentId()
				)
			);

		return draftToken;
	}

	public void finalizeLecture(HttpSession session, Long participantLectureId) {
		RegistrationFlow flow = registrationFlow(session);

		setRegistrationFlow(
			session,
			new RegistrationFlow(
				flow.request(),
				participantLectureId,
					null,
					flow.editingParticipantId(),
					flow.returnToLookupAfterEdit(),
					false,
					flow.registrationRecruitmentId()
				)
			);
	}

	public LookupFlow lookupFlow(HttpSession session) {
		Object attribute = session.getAttribute(LOOKUP_FLOW_SESSION_KEY);

		if (attribute instanceof LookupFlow lookupFlow) {
			return lookupFlow;
		}

		return LookupFlow.empty();
	}

	public void saveLookupCondition(
		HttpSession session,
		String name,
		Long churchId,
		String phoneNumber
	) {
		LookupFlow flow = lookupFlow(session);

		setLookupFlow(
			session,
			new LookupFlow(name, churchId, phoneNumber, flow.participantLectureIds())
		);
	}

	public void saveLookupResultIds(
		HttpSession session,
		List<Long> participantLectureIds
	) {
		LookupFlow flow = lookupFlow(session);

		setLookupFlow(
			session,
			new LookupFlow(flow.name(), flow.churchId(), flow.phoneNumber(), participantLectureIds)
		);
	}

	public boolean canAccessLookupResult(
		HttpSession session,
		Long participantLectureId
	) {
		return lookupFlow(session).participantLectureIds().contains(participantLectureId);
	}

	public void setAdminReturnUrl(
		HttpSession session,
		String adminReturnUrl
	) {
		session.setAttribute(ADMIN_RETURN_URL_SESSION_KEY, adminReturnUrl);
	}

	public String adminReturnUrl(HttpSession session) {
		return (String)session.getAttribute(ADMIN_RETURN_URL_SESSION_KEY);
	}

	public String consumeAdminReturnUrl(HttpSession session) {
		String adminReturnUrl = adminReturnUrl(session);

		if (adminReturnUrl != null) {
			session.removeAttribute(ADMIN_RETURN_URL_SESSION_KEY);
		}

		return adminReturnUrl;
	}

	private RegistrationFlow registrationFlow(HttpSession session) {
		Object attribute = session.getAttribute(REGISTRATION_FLOW_SESSION_KEY);

		if (attribute instanceof RegistrationFlow registrationFlow) {
			return registrationFlow;
		}

		return RegistrationFlow.empty();
	}

	private void setRegistrationFlow(
		HttpSession session,
		RegistrationFlow flow
	) {
		session.setAttribute(REGISTRATION_FLOW_SESSION_KEY, flow);
	}

	private void setLookupFlow(
		HttpSession session,
		LookupFlow flow
	) {
		session.setAttribute(LOOKUP_FLOW_SESSION_KEY, flow);
	}

	private String createDraftToken() {
		return UUID.randomUUID().toString();
	}

	private record RegistrationFlow(
		ParticipantCreateRequest request,
		Long participantLectureId,
		String draftToken,
		Long editingParticipantId,
		boolean returnToLookupAfterEdit,
		boolean lectureEditMode,
		Long registrationRecruitmentId
	) {
		private static RegistrationFlow empty() {
			return new RegistrationFlow(null, null, null, null, false, false, null);
		}
	}

	public record LookupFlow(
		String name,
		Long churchId,
		String phoneNumber,
		List<Long> participantLectureIds
	) {
		private static LookupFlow empty() {
			return new LookupFlow(null, null, null, List.of());
		}

		public boolean hasCondition() {
			return name != null && churchId != null && phoneNumber != null;
		}
	}
}
