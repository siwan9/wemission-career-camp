package com.wemisson.career_camp.domain.admin.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
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

import com.wemisson.career_camp.domain.participant.dto.ParticipantType;
import com.wemisson.career_camp.domain.participant.dto.ParticipantCreateRequest;
import com.wemisson.career_camp.domain.participant.entity.ParticipantLectureDraftEntity;
import com.wemisson.career_camp.domain.participant.entity.ParticipantTypeEntity;
import com.wemisson.career_camp.domain.participant.repository.ParticipantLectureDraftRepository;
import com.wemisson.career_camp.domain.participant.repository.ParticipantLectureRepository;
import com.wemisson.career_camp.domain.participant.repository.ParticipantRepository;
import com.wemisson.career_camp.domain.participant.repository.ParticipantTypeRepository;
import com.wemisson.career_camp.domain.participant.service.command.LectureApplicationService;
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
import com.wemisson.career_camp.domain.recruitment.scheduler.RecruitmentStatusScheduler;
import com.wemisson.career_camp.domain.recruitment.service.query.RecruitmentQueryService;

@SpringBootTest
@ActiveProfiles("test")
class AdminRecruitmentCommandServiceTest {

	@Autowired
	private AdminRecruitmentCommandService adminRecruitmentCommandService;
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
	@Autowired
	private RecruitmentStatusScheduler recruitmentStatusScheduler;
	@Autowired
	private LectureApplicationService lectureApplicationService;

	private RecruitmentEntity recruitment;
	private ParticipantTypeEntity studentType;
	private RecruitmentChurchEntity church;
	private LectureEntity morningLecture;

	@BeforeEach
	void setUp() {
		clearData();

		recruitment = recruitmentRepository.save(RecruitmentEntity.create(
			"테스트 모집",
			"설명",
			"공지",
			LocalDateTime.now().minusDays(1),
			LocalDateTime.now().plusDays(1),
			RecruitmentStatus.CLOSED
		));
		studentType = participantTypeRepository.save(ParticipantTypeEntity.from(ParticipantType.STUDENT));
		church = recruitmentChurchRepository.save(
			RecruitmentChurchEntity.create(recruitment, "테스트 교회", 1)
		);
		recruitmentParticipantTypeRepository.save(
			RecruitmentParticipantTypeEntity.create(recruitment, studentType, true, false)
		);
		morningLecture = lectureRepository.save(
			LectureEntity.create(recruitment, "오전", "강사", "설명", LectureType.AM, true, 3, 1)
		);
		recruitmentQueryService.evictRecruitmentCaches(recruitment.getId());
	}

	@AfterEach
	void tearDown() {
		clearData();
	}

	@Test
	void 강좌_정원은_확정_신청자와_임시점유_인원보다_낮게_줄일_수_없다() {
		participantLectureDraftRepository.save(ParticipantLectureDraftEntity.create(
			"draft-1",
			null,
			recruitment,
			morningLecture,
			LocalDateTime.now().plusMinutes(3),
			LocalDateTime.now()
		));
		participantLectureDraftRepository.save(ParticipantLectureDraftEntity.create(
			"draft-2",
			null,
			recruitment,
			morningLecture,
			LocalDateTime.now().plusMinutes(3),
			LocalDateTime.now()
		));

		assertThatThrownBy(() -> adminRecruitmentCommandService.updateLecture(
			recruitment.getId(),
			morningLecture.getId(),
			"오전",
			"강사",
			"설명",
			LectureType.AM,
			true,
			1
		))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("임시점유");

		adminRecruitmentCommandService.updateLecture(
			recruitment.getId(),
			morningLecture.getId(),
			"오전",
			"강사",
			"설명",
			LectureType.AM,
			true,
			2
		);

		assertThat(lectureRepository.findById(morningLecture.getId()).orElseThrow().getMaxCapacity()).isEqualTo(2);
	}

	@Test
	void 모집을_시작하려면_교회_참가자타입_신청가능_강좌가_준비되어야_한다() {
		RecruitmentEntity emptyRecruitment = recruitmentRepository.save(RecruitmentEntity.create(
			"빈 모집",
			"설명",
			"공지",
			LocalDateTime.now().minusDays(1),
			LocalDateTime.now().plusDays(1),
			RecruitmentStatus.CLOSED
		));

		assertThatThrownBy(() -> adminRecruitmentCommandService.changeRecruitmentStatus(
			emptyRecruitment.getId(),
			RecruitmentStatus.OPEN
		))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("교회 목록");

		assertThat(adminRecruitmentCommandService.changeRecruitmentStatus(
			recruitment.getId(),
			RecruitmentStatus.OPEN
		)).isEqualTo(RecruitmentStatus.OPEN);
	}

	@Test
	void 준비중인_강좌가_있어도_신청가능한_필수_시간대_강좌가_있으면_모집을_시작할_수_있다() {
		lectureRepository.save(
			LectureEntity.create(recruitment, "대기중", "강사", "설명", LectureType.AM, false, 3, 2)
		);

		assertThat(adminRecruitmentCommandService.changeRecruitmentStatus(
			recruitment.getId(),
			RecruitmentStatus.OPEN
		)).isEqualTo(RecruitmentStatus.OPEN);
	}

	@Test
	void 대기중_모집이_있으면_다른_지난_모집을_모집중으로_바꿀_수_없다() {
		RecruitmentEntity waitingRecruitment = recruitmentRepository.save(RecruitmentEntity.create(
			"대기중 모집",
			"설명",
			"공지",
			LocalDateTime.now().minusDays(1),
			LocalDateTime.now().plusDays(1),
			RecruitmentStatus.WAITING
		));
		recruitmentChurchRepository.save(RecruitmentChurchEntity.create(waitingRecruitment, "대기중 교회", 1));
		recruitmentParticipantTypeRepository.save(
			RecruitmentParticipantTypeEntity.create(waitingRecruitment, studentType, true, false)
		);
		lectureRepository.save(
			LectureEntity.create(waitingRecruitment, "대기중 오전", "강사", "설명", LectureType.AM, true, 3, 1)
		);

		assertThatThrownBy(() -> adminRecruitmentCommandService.changeRecruitmentStatus(
			recruitment.getId(),
			RecruitmentStatus.OPEN
		))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("대기중");

		assertThat(adminRecruitmentCommandService.changeRecruitmentStatus(
			waitingRecruitment.getId(),
			RecruitmentStatus.OPEN
		)).isEqualTo(RecruitmentStatus.OPEN);
	}

	@Test
	void 스케줄러는_대기중_모집이_있으면_다른_지난_모집을_자동으로_모집중으로_바꾸지_않는다() {
		RecruitmentEntity triggeredClosedRecruitment = recruitmentRepository.save(RecruitmentEntity.create(
			"시작시간 지난 모집",
			"설명",
			"공지",
			LocalDateTime.now().minusSeconds(10),
			LocalDateTime.now().plusDays(1),
			RecruitmentStatus.CLOSED
		));
		RecruitmentEntity waitingRecruitment = recruitmentRepository.save(RecruitmentEntity.create(
			"대기중 모집",
			"설명",
			"공지",
			LocalDateTime.now().plusDays(1),
			LocalDateTime.now().plusDays(2),
			RecruitmentStatus.WAITING
		));

		recruitmentStatusScheduler.synchronizeRecruitmentStatus();

		assertThat(recruitmentRepository.findById(triggeredClosedRecruitment.getId()).orElseThrow().getStatus())
			.isEqualTo(RecruitmentStatus.CLOSED);
		assertThat(recruitmentRepository.findById(waitingRecruitment.getId()).orElseThrow().getStatus())
			.isEqualTo(RecruitmentStatus.WAITING);
	}

	@Test
	void 서로_다른_모집을_동시에_열어도_하나의_모집만_열린다() throws Exception {
		RecruitmentEntity otherRecruitment = recruitmentRepository.save(RecruitmentEntity.create(
			"다른 모집",
			"설명",
			"공지",
			LocalDateTime.now().minusDays(1),
			LocalDateTime.now().plusDays(1),
			RecruitmentStatus.CLOSED
		));
		recruitmentChurchRepository.save(RecruitmentChurchEntity.create(otherRecruitment, "다른 교회", 1));
		recruitmentParticipantTypeRepository.save(
			RecruitmentParticipantTypeEntity.create(otherRecruitment, studentType, true, false)
		);
		lectureRepository.save(
			LectureEntity.create(otherRecruitment, "다른 오전", "강사", "설명", LectureType.AM, true, 3, 1)
		);
		ExecutorService executorService = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		try {
			Future<Boolean> first = executorService.submit(
				() -> changeStatusAfterSignal(ready, start, recruitment.getId())
			);
			Future<Boolean> second = executorService.submit(
				() -> changeStatusAfterSignal(ready, start, otherRecruitment.getId())
			);

			ready.await();
			start.countDown();

			assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
			assertThat(recruitmentRepository.findByStatusOrderByIdDesc(RecruitmentStatus.OPEN)).hasSize(1);
		} finally {
			executorService.shutdownNow();
		}
	}

	@Test
	void 관리자_강좌수정과_최종확정이_겹쳐도_신청인원이_유실되지_않는다() throws Exception {
		ParticipantCreateRequest request = new ParticipantCreateRequest(
			"동시 관리자",
			studentType.getId(),
			church.getId(),
			"01045454545"
		);
		String draftToken = "admin-update-finalize-token";
		lectureApplicationService.holdDraft(
			request,
			null,
			recruitment.getId(),
			morningLecture.getId(),
			draftToken,
			true
		);
		ExecutorService executorService = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		try {
			Future<?> finalizeFuture = executorService.submit(() -> {
				ready.countDown();
				start.await();
				return lectureApplicationService.finalizeDraft(
					request,
					null,
					recruitment.getId(),
					draftToken,
					true
				);
			});
			Future<?> updateFuture = executorService.submit(() -> {
				ready.countDown();
				start.await();
				adminRecruitmentCommandService.updateLecture(
					recruitment.getId(),
					morningLecture.getId(),
					"수정된 오전",
					"강사",
					"설명",
					LectureType.AM,
					true,
					3
				);
				return null;
			});

			ready.await();
			start.countDown();
			finalizeFuture.get();
			updateFuture.get();

			LectureEntity savedLecture = lectureRepository.findById(morningLecture.getId()).orElseThrow();
			assertThat(savedLecture.getParticipantCount()).isEqualTo(1);
			assertThat(savedLecture.getSpeakerName()).isEqualTo("수정된 오전");
			assertThat(participantLectureRepository.findAll()).hasSize(1);
		} finally {
			executorService.shutdownNow();
		}
	}

	private boolean changeStatusAfterSignal(
		CountDownLatch ready,
		CountDownLatch start,
		Long recruitmentId
	) throws InterruptedException {
		ready.countDown();
		start.await();

		try {
			adminRecruitmentCommandService.changeRecruitmentStatus(recruitmentId, RecruitmentStatus.OPEN);
			return true;
		} catch (IllegalStateException exception) {
			return false;
		}
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
