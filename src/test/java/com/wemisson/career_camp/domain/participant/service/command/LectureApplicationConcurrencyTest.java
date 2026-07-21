package com.wemisson.career_camp.domain.participant.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.wemisson.career_camp.domain.participant.dto.ParticipantCreateRequest;
import com.wemisson.career_camp.domain.participant.dto.ParticipantLectureDraftResult;
import com.wemisson.career_camp.domain.participant.dto.ParticipantType;
import com.wemisson.career_camp.domain.participant.dto.LectureApplicationResult;
import com.wemisson.career_camp.domain.participant.entity.ParticipantLectureDraftEntity;
import com.wemisson.career_camp.domain.participant.entity.ParticipantTypeEntity;
import com.wemisson.career_camp.domain.participant.repository.ParticipantLectureDraftRepository;
import com.wemisson.career_camp.domain.participant.repository.ParticipantLectureRepository;
import com.wemisson.career_camp.domain.participant.repository.ParticipantRepository;
import com.wemisson.career_camp.domain.participant.repository.ParticipantTypeRepository;
import com.wemisson.career_camp.domain.recruitment.dto.LectureType;
import com.wemisson.career_camp.domain.recruitment.dto.RecruitmentStatus;
import com.wemisson.career_camp.domain.recruitment.entity.LectureEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentChurchEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentParticipantTypeEntity;
import com.wemisson.career_camp.domain.recruitment.repository.LectureRepository;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentChurchRepository;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentParticipantTypeRepository;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentRepository;
import com.wemisson.career_camp.domain.recruitment.service.query.RecruitmentQueryService;

@SpringBootTest
@ActiveProfiles("test")
class LectureApplicationConcurrencyTest {

	@Autowired
	private Clock clock;
	@Autowired
	private LectureApplicationService lectureApplicationService;
	@Autowired
	private ParticipantLookupService participantLookupService;
	@Autowired
	private RecruitmentQueryService recruitmentQueryService;
	@Autowired
	private RecruitmentRepository recruitmentRepository;
	@Autowired
	private LectureRepository lectureRepository;
	@Autowired
	private RecruitmentChurchRepository recruitmentChurchRepository;
	@Autowired
	private RecruitmentParticipantTypeRepository recruitmentParticipantTypeRepository;
	@Autowired
	private ParticipantTypeRepository participantTypeRepository;
	@Autowired
	private ParticipantRepository participantRepository;
	@Autowired
	private ParticipantLectureRepository participantLectureRepository;
	@Autowired
	private ParticipantLectureDraftRepository participantLectureDraftRepository;

	private RecruitmentEntity recruitment;
	private RecruitmentChurchEntity church;
	private ParticipantTypeEntity studentType;
	private LectureEntity morningLecture;
	private LectureEntity anotherMorningLecture;
	private LectureEntity afternoonLecture;

	@BeforeEach
	void setUp() {
		clearData();

		recruitment = recruitmentRepository.save(RecruitmentEntity.create(
			"테스트 모집",
			"설명",
			"공지",
			LocalDateTime.now(clock).minusDays(1),
			LocalDateTime.now(clock).plusDays(1),
			RecruitmentStatus.OPEN
		));
		studentType = participantTypeRepository.save(ParticipantTypeEntity.from(ParticipantType.STUDENT));
		church = recruitmentChurchRepository.save(RecruitmentChurchEntity.create(recruitment, "테스트 교회", 1));
		recruitmentParticipantTypeRepository.save(
			RecruitmentParticipantTypeEntity.create(recruitment, studentType, true, true)
		);
		morningLecture = lectureRepository.save(
			LectureEntity.create(recruitment, "오전", "강사", "설명", LectureType.AM, true, 1, 1)
		);
		anotherMorningLecture = lectureRepository.save(
			LectureEntity.create(recruitment, "다른 오전", "강사", "설명", LectureType.AM, true, 2, 1)
		);
		afternoonLecture = lectureRepository.save(
			LectureEntity.create(recruitment, "오후", "강사", "설명", LectureType.PM, true, 1, 1)
		);
		recruitmentQueryService.evictRecruitmentCaches(recruitment.getId());
	}

	@AfterEach
	void tearDown() {
		clearData();
	}

	@Test
	void 마지막_남은_자리는_동시에_점유해도_한_명만_성공한다() throws Exception {
		ExecutorService executorService = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ParticipantCreateRequest firstRequest = createRequest("첫번째", "01011111111");
		ParticipantCreateRequest secondRequest = createRequest("두번째", "01022222222");

		try {
			Future<Boolean> first = executorService.submit(holdAfterSignal(
				ready,
				start,
				firstRequest,
				"draft-1",
				morningLecture.getId()
			));
			Future<Boolean> second = executorService.submit(holdAfterSignal(
				ready,
				start,
				secondRequest,
				"draft-2",
				morningLecture.getId()
			));

			ready.await();
			start.countDown();

			List<Boolean> results = List.of(first.get(), second.get());

			assertThat(results).containsExactlyInAnyOrder(true, false);
			assertThat(participantLectureDraftRepository.countByLectureEntityAndExpiresAtAfter(
				morningLecture,
				LocalDateTime.now(clock)
			)).isEqualTo(1);
		} finally {
			executorService.shutdownNow();
		}
	}

	@Test
	void 동일_강좌에_50명이_동시에_점유해도_정원_10명만_성공한다() throws Exception {
		int requestCount = 50;
		int capacity = 10;
		LectureEntity contestedLecture = lectureRepository.save(
			LectureEntity.create(recruitment, "동시접속 오전", "강사", "설명", LectureType.AM, true, capacity, 4)
		);
		ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
		CountDownLatch ready = new CountDownLatch(requestCount);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> futures = new java.util.ArrayList<>();

		try {
			for (int index = 0; index < requestCount; index++) {
				ParticipantCreateRequest request = createRequest(
					"동시신청" + index,
					String.format("010%08d", index)
				);
				String draftToken = "contested-token-" + index;

				futures.add(executorService.submit(() -> {
					ready.countDown();
					start.await();
					try {
						holdDraft(request, null, contestedLecture.getId(), draftToken, false);
						return true;
					} catch (RuntimeException exception) {
						return false;
					}
				}));
			}

			ready.await();
			start.countDown();
			long successCount = 0;

			for (Future<Boolean> future : futures) {
				if (future.get()) {
					successCount++;
				}
			}

			assertThat(successCount).isEqualTo(capacity);
			assertThat(participantLectureDraftRepository.countByLectureEntityAndExpiresAtAfter(
				contestedLecture,
				LocalDateTime.now(clock)
			)).isEqualTo(capacity);
		} finally {
			executorService.shutdownNow();
		}
	}

	@Test
	void 여러_점유_중_하나를_취소하면_남은_자리는_남아있는_점유를_반영한다() {
		LectureEntity roomyMorningLecture = lectureRepository.save(
			LectureEntity.create(recruitment, "여유 오전", "강사", "설명", LectureType.AM, true, 3, 3)
		);
		ParticipantCreateRequest firstRequest = createRequest("첫번째", "01077777777");
		ParticipantCreateRequest secondRequest = createRequest("두번째", "01088888888");

		holdDraft(firstRequest, null, roomyMorningLecture.getId(), "orphan-token", false);
		holdDraft(secondRequest, null, roomyMorningLecture.getId(), "active-token", false);

		assertThat(participantLectureDraftRepository.countByLectureEntityAndExpiresAtAfter(
			roomyMorningLecture,
			LocalDateTime.now(clock)
		)).isEqualTo(2);

		assertThat(releaseDraft(
			secondRequest,
			null,
			roomyMorningLecture.getId(),
			"active-token"
		).remainingCapacity()).isEqualTo(2);
		assertThat(participantLectureDraftRepository.countByLectureEntityAndExpiresAtAfter(
			roomyMorningLecture,
			LocalDateTime.now(clock)
		)).isEqualTo(1);
	}

	@Test
	void 같은_시간대에_이미_점유한_특강이_있으면_취소_전에는_다른_특강을_점유할_수_없다() {
		ParticipantCreateRequest request = createRequest("변경시도", "01066666666");
		String draftToken = "same-type-token";

		holdDraft(request, null, morningLecture.getId(), draftToken, false);

		assertThatThrownBy(() -> holdDraft(
			request,
			null,
			anotherMorningLecture.getId(),
			draftToken,
			false
		))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("선택 해제");

		assertThat(participantLectureDraftRepository.findByDraftTokenAndLectureType(
			draftToken,
			LectureType.AM
		))
			.hasValueSatisfying(draft -> assertThat(draft.getLectureEntity().getId()).isEqualTo(morningLecture.getId()));

		releaseDraft(request, null, morningLecture.getId(), draftToken);
		holdDraft(request, null, anotherMorningLecture.getId(), draftToken, false);

		assertThat(participantLectureDraftRepository.findByDraftTokenAndLectureType(
			draftToken,
			LectureType.AM
		))
			.hasValueSatisfying(draft -> assertThat(draft.getLectureEntity().getId()).isEqualTo(
				anotherMorningLecture.getId()
			));
	}

	@Test
	void 수강신청_수정_중_새_특강을_점유했다가_해제해도_기존_신청은_유지된다() {
		ParticipantCreateRequest request = createRequest("수정이탈", "01012121212");
		String initialDraftToken = "edit-keep-initial-token";
		String editDraftToken = "edit-keep-draft-token";

		holdDraft(request, null, morningLecture.getId(), initialDraftToken, false);
		holdDraft(request, null, afternoonLecture.getId(), initialDraftToken, false);
		Long participantLectureId = finalizeDraft(request, null, initialDraftToken, false)
			.participantLectureId();

		assertThat(lectureRepository.findById(morningLecture.getId()).orElseThrow().getParticipantCount()).isEqualTo(1);
		assertThat(lectureRepository.findById(anotherMorningLecture.getId()).orElseThrow().getParticipantCount()).isZero();

		holdDraft(
			request,
			participantLectureId,
			anotherMorningLecture.getId(),
			editDraftToken,
			false,
			true
		);
		assertThat(lectureRepository.findById(morningLecture.getId()).orElseThrow().getParticipantCount()).isEqualTo(1);
		assertThat(participantLectureDraftRepository.countByLectureEntityAndExpiresAtAfter(
			anotherMorningLecture,
			LocalDateTime.now(clock)
		)).isEqualTo(1);

		releaseDraft(request, participantLectureId, anotherMorningLecture.getId(), editDraftToken);

		assertThat(lectureRepository.findById(morningLecture.getId()).orElseThrow().getParticipantCount()).isEqualTo(1);
		assertThat(lectureRepository.findById(anotherMorningLecture.getId()).orElseThrow().getParticipantCount()).isZero();
		assertThat(participantLectureRepository.findById(participantLectureId).orElseThrow()
			.getMorningLectureEntity()
			.getId()).isEqualTo(morningLecture.getId());
	}

	@Test
	void 수강신청_수정은_최종_신청을_눌렀을_때만_기존_강좌에서_새_강좌로_교체된다() {
		ParticipantCreateRequest request = createRequest("수정완료", "01013131313");
		String initialDraftToken = "edit-finalize-initial-token";
		String editDraftToken = "edit-finalize-draft-token";

		holdDraft(request, null, morningLecture.getId(), initialDraftToken, false);
		holdDraft(request, null, afternoonLecture.getId(), initialDraftToken, false);
		Long participantLectureId = finalizeDraft(request, null, initialDraftToken, false)
			.participantLectureId();

		holdDraft(
			request,
			participantLectureId,
			anotherMorningLecture.getId(),
			editDraftToken,
			false,
			true
		);

		assertThat(lectureRepository.findById(morningLecture.getId()).orElseThrow().getParticipantCount()).isEqualTo(1);
		assertThat(lectureRepository.findById(anotherMorningLecture.getId()).orElseThrow().getParticipantCount()).isZero();

		finalizeDraft(request, participantLectureId, editDraftToken, false);

		assertThat(lectureRepository.findById(morningLecture.getId()).orElseThrow().getParticipantCount()).isZero();
		assertThat(lectureRepository.findById(anotherMorningLecture.getId()).orElseThrow().getParticipantCount()).isEqualTo(1);
		assertThat(participantLectureRepository.findById(participantLectureId).orElseThrow()
			.getMorningLectureEntity()
			.getId()).isEqualTo(anotherMorningLecture.getId());
		assertThat(participantLectureDraftRepository.findByDraftTokenAndExpiresAtAfter(
			editDraftToken,
			LocalDateTime.now(clock)
		)).isEmpty();
	}

	@Test
	void 신청을_완료한_뒤_수정_모드가_아니면_다른_특강을_점유할_수_없다() {
		ParticipantCreateRequest request = createRequest("완료자", "01099999999");
		String draftToken = "completed-token";

		holdDraft(request, null, morningLecture.getId(), draftToken, false);
		holdDraft(request, null, afternoonLecture.getId(), draftToken, false);
		Long participantLectureId = finalizeDraft(request, null, draftToken, false)
			.participantLectureId();

		assertThatThrownBy(() -> holdDraft(
			request,
			participantLectureId,
			anotherMorningLecture.getId(),
			"completed-new-token",
			false,
			false
		))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("수정 화면");
	}

	@Test
	void 필수_특강을_모두_점유한_뒤_최종_신청하면_신청이_확정되고_draft가_삭제된다() {
		ParticipantCreateRequest request = createRequest("신청자", "01033333333");
		String draftToken = "finalize-token";

		holdDraft(request, null, morningLecture.getId(), draftToken, false);
		holdDraft(request, null, afternoonLecture.getId(), draftToken, false);

		finalizeDraft(request, null, draftToken, false);

		LectureEntity savedMorningLecture = lectureRepository.findById(morningLecture.getId()).orElseThrow();
		LectureEntity savedAfternoonLecture = lectureRepository.findById(afternoonLecture.getId()).orElseThrow();

		assertThat(savedMorningLecture.getParticipantCount()).isEqualTo(1);
		assertThat(savedAfternoonLecture.getParticipantCount()).isEqualTo(1);
		assertThat(participantLectureDraftRepository.findByDraftTokenAndExpiresAtAfter(
			draftToken,
			LocalDateTime.now(clock)
		)).isEmpty();
		assertThat(participantRepository.countByRecruitmentEntity(recruitment)).isEqualTo(1);
		assertThat(participantLectureRepository.findAll()).hasSize(1);
	}

	@Test
	void 같은_임시점유를_동시에_최종확정해도_신청은_한_번만_반영된다() throws Exception {
		ParticipantCreateRequest request = createRequest("동시확정", "01034343434");
		String draftToken = "concurrent-finalize-token";
		holdDraft(request, null, morningLecture.getId(), draftToken, false);
		holdDraft(request, null, afternoonLecture.getId(), draftToken, false);
		ExecutorService executorService = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		try {
			Future<Boolean> first = executorService.submit(finalizeAfterSignal(ready, start, request, draftToken));
			Future<Boolean> second = executorService.submit(finalizeAfterSignal(ready, start, request, draftToken));

			ready.await();
			start.countDown();

			assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
			assertThat(lectureRepository.findById(morningLecture.getId()).orElseThrow().getParticipantCount()).isEqualTo(1);
			assertThat(lectureRepository.findById(afternoonLecture.getId()).orElseThrow().getParticipantCount()).isEqualTo(1);
			assertThat(participantRepository.countByRecruitmentEntity(recruitment)).isEqualTo(1);
			assertThat(participantLectureRepository.findAll()).hasSize(1);
			assertThat(participantLectureDraftRepository.findAll())
				.noneMatch(draft -> draftToken.equals(draft.getDraftToken()));
		} finally {
			executorService.shutdownNow();
		}
	}

	@Test
	void 만료된_임시점유는_최종확정할_수_없다() {
		ParticipantCreateRequest request = createRequest("만료점유", "01035353535");
		String draftToken = "expired-finalize-token";
		LocalDateTime now = LocalDateTime.now(clock);

		participantLectureDraftRepository.save(ParticipantLectureDraftEntity.create(
			draftToken,
			null,
			recruitment,
			morningLecture,
			now.minusSeconds(1),
			now.minusMinutes(4)
		));
		participantLectureDraftRepository.save(ParticipantLectureDraftEntity.create(
			draftToken,
			null,
			recruitment,
			afternoonLecture,
			now.minusSeconds(1),
			now.minusMinutes(4)
		));

		assertThatThrownBy(() -> finalizeDraft(request, null, draftToken, false))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("만료");
		assertThat(participantRepository.countByRecruitmentEntity(recruitment)).isZero();
		assertThat(lectureRepository.findById(morningLecture.getId()).orElseThrow().getParticipantCount()).isZero();
		assertThat(lectureRepository.findById(afternoonLecture.getId()).orElseThrow().getParticipantCount()).isZero();
	}

	@Test
	void 수정_중인_임시점유가_만료되면_기존_신청으로_조용히_확정하지_않는다() {
		ParticipantCreateRequest request = createRequest("수정만료", "01037373737");
		String initialDraftToken = "expired-edit-initial-token";
		String editDraftToken = "expired-edit-token";

		holdDraft(request, null, morningLecture.getId(), initialDraftToken, false);
		holdDraft(request, null, afternoonLecture.getId(), initialDraftToken, false);
		Long participantLectureId = finalizeDraft(request, null, initialDraftToken, false)
			.participantLectureId();
		LocalDateTime now = LocalDateTime.now(clock);

		participantLectureDraftRepository.save(ParticipantLectureDraftEntity.create(
			editDraftToken,
			participantLectureRepository.findById(participantLectureId).orElseThrow().getParticipantEntity(),
			recruitment,
			anotherMorningLecture,
			now.minusSeconds(1),
			now.minusMinutes(4)
		));

		assertThatThrownBy(() -> finalizeDraft(request, participantLectureId, editDraftToken, false))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("만료");
		assertThat(participantLectureRepository.findById(participantLectureId).orElseThrow()
			.getMorningLectureEntity()
			.getId()).isEqualTo(morningLecture.getId());
		assertThat(lectureRepository.findById(morningLecture.getId()).orElseThrow().getParticipantCount()).isEqualTo(1);
		assertThat(lectureRepository.findById(anotherMorningLecture.getId()).orElseThrow().getParticipantCount()).isZero();
	}

	@Test
	void 현재_모집과_다른_모집의_강좌는_점유할_수_없다() {
		RecruitmentEntity otherRecruitment = recruitmentRepository.save(RecruitmentEntity.create(
			"다른 모집",
			"설명",
			"공지",
			LocalDateTime.now(clock).minusDays(1),
			LocalDateTime.now(clock).plusDays(1),
			RecruitmentStatus.CLOSED
		));
		LectureEntity otherLecture = lectureRepository.save(
			LectureEntity.create(otherRecruitment, "다른 오전", "강사", "설명", LectureType.AM, true, 10, 1)
		);
		ParticipantCreateRequest request = createRequest("범위검증", "01036363636");

		assertThatThrownBy(() -> lectureApplicationService.holdDraft(
			request,
			null,
			recruitment.getId(),
			otherLecture.getId(),
			"cross-recruitment-token",
			false
		))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("현재 신청 중인 모집");
		assertThat(participantLectureDraftRepository.findAll()).isEmpty();
	}

	@Test
	void 필수_특강을_모두_선택하지_않으면_최종_신청할_수_없다() {
		ParticipantCreateRequest request = createRequest("미완료", "01044444444");
		String draftToken = "missing-required-token";

		holdDraft(request, null, morningLecture.getId(), draftToken, false);

		assertThatThrownBy(() -> finalizeDraft(request, null, draftToken, false))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("필수 특강");
	}

	@Test
	void 조회_후_삭제하면_확정_신청과_참가자_정보와_강좌_카운트가_함께_정리된다() {
		ParticipantCreateRequest request = createRequest("삭제대상", "01055555555");
		String draftToken = "delete-token";

		holdDraft(request, null, morningLecture.getId(), draftToken, false);
		holdDraft(request, null, afternoonLecture.getId(), draftToken, false);
		Long participantLectureId = finalizeDraft(request, null, draftToken, false)
			.participantLectureId();

		participantLookupService.deleteParticipantLecture(participantLectureId);

		assertThat(participantLectureRepository.findById(participantLectureId)).isEmpty();
		assertThat(participantRepository.countByRecruitmentEntity(recruitment)).isZero();
		assertThat(lectureRepository.findById(morningLecture.getId()).orElseThrow().getParticipantCount()).isZero();
		assertThat(lectureRepository.findById(afternoonLecture.getId()).orElseThrow().getParticipantCount()).isZero();
	}

	private ParticipantLectureDraftResult holdDraft(
		ParticipantCreateRequest request,
		Long participantLectureId,
		Long lectureId,
		String draftToken,
		boolean allowFull
	) {
		return lectureApplicationService.holdDraft(
			request,
			participantLectureId,
			recruitment.getId(),
			lectureId,
			draftToken,
			allowFull
		);
	}

	private ParticipantLectureDraftResult holdDraft(
		ParticipantCreateRequest request,
		Long participantLectureId,
		Long lectureId,
		String draftToken,
		boolean allowFull,
		boolean allowCompletedApplicationModification
	) {
		return lectureApplicationService.holdDraft(
			request,
			participantLectureId,
			recruitment.getId(),
			lectureId,
			draftToken,
			allowFull,
			allowCompletedApplicationModification
		);
	}

	private ParticipantLectureDraftResult releaseDraft(
		ParticipantCreateRequest request,
		Long participantLectureId,
		Long lectureId,
		String draftToken
	) {
		return lectureApplicationService.releaseDraft(
			request,
			participantLectureId,
			recruitment.getId(),
			lectureId,
			draftToken
		);
	}

	private LectureApplicationResult finalizeDraft(
		ParticipantCreateRequest request,
		Long participantLectureId,
		String draftToken,
		boolean allowFull
	) {
		return lectureApplicationService.finalizeDraft(
			request,
			participantLectureId,
			recruitment.getId(),
			draftToken,
			allowFull
		);
	}

	private Callable<Boolean> holdAfterSignal(
		CountDownLatch ready,
		CountDownLatch start,
		ParticipantCreateRequest request,
		String draftToken,
		Long lectureId
	) {
		return () -> {
			ready.countDown();
			start.await();
			try {
				holdDraft(request, null, lectureId, draftToken, false);
				return true;
			} catch (IllegalStateException e) {
				return false;
			}
		};
	}

	private Callable<Boolean> finalizeAfterSignal(
		CountDownLatch ready,
		CountDownLatch start,
		ParticipantCreateRequest request,
		String draftToken
	) {
		return () -> {
			ready.countDown();
			start.await();
			try {
				finalizeDraft(request, null, draftToken, false);
				return true;
			} catch (RuntimeException exception) {
				return false;
			}
		};
	}

	private ParticipantCreateRequest createRequest(
		String name,
		String phoneNumber
	) {
		return new ParticipantCreateRequest(
			name,
			studentType.getId(),
			church.getId(),
			phoneNumber
		);
	}

	private void clearData() {
		participantLectureDraftRepository.deleteAll();
		participantLectureRepository.deleteAll();
		participantRepository.deleteAll();
		lectureRepository.deleteAll();
		recruitmentParticipantTypeRepository.deleteAll();
		recruitmentChurchRepository.deleteAll();
		recruitmentRepository.deleteAll();
		participantTypeRepository.deleteAll();
	}
}
