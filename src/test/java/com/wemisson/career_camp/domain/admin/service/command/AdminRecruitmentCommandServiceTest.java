package com.wemisson.career_camp.domain.admin.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
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
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentParticipantTypeFixedLectureRepository;
import com.wemisson.career_camp.domain.recruitment.repository.LectureRepository;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentChurchRepository;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentParticipantTypeRepository;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentRepository;
import com.wemisson.career_camp.domain.recruitment.scheduler.RecruitmentStatusScheduler;
import com.wemisson.career_camp.domain.recruitment.service.query.LectureCatalogQueryService;
import com.wemisson.career_camp.domain.recruitment.service.query.RecruitmentQueryService;

@SpringBootTest
@ActiveProfiles("test")
class AdminRecruitmentCommandServiceTest {

	@Autowired
	private Clock clock;
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
	private RecruitmentParticipantTypeFixedLectureRepository fixedLectureRepository;
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
	@Autowired
	private LectureCatalogQueryService lectureCatalogQueryService;

	private RecruitmentEntity recruitment;
	private ParticipantTypeEntity studentType;
	private RecruitmentChurchEntity church;
	private LectureEntity morningLecture;
	private RecruitmentParticipantTypeEntity studentRule;

	@BeforeEach
	void setUp() {
		clearData();

		recruitment = recruitmentRepository.save(RecruitmentEntity.create(
			"테스트 모집",
			"설명",
			"공지",
			LocalDateTime.now(clock).minusDays(1),
			LocalDateTime.now(clock).plusDays(1),
			RecruitmentStatus.CLOSED
		));
		studentType = participantTypeRepository.save(ParticipantTypeEntity.from(ParticipantType.STUDENT));
		church = recruitmentChurchRepository.save(
			RecruitmentChurchEntity.create(recruitment, "테스트 교회", 1)
		);
		studentRule = recruitmentParticipantTypeRepository.save(
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
			LocalDateTime.now(clock).plusMinutes(3),
			LocalDateTime.now(clock)
		));
		participantLectureDraftRepository.save(ParticipantLectureDraftEntity.create(
			"draft-2",
			null,
			recruitment,
			morningLecture,
			LocalDateTime.now(clock).plusMinutes(3),
			LocalDateTime.now(clock)
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
			LocalDateTime.now(clock).minusDays(1),
			LocalDateTime.now(clock).plusDays(1),
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
			LocalDateTime.now(clock).minusDays(1),
			LocalDateTime.now(clock).plusDays(1),
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
			LocalDateTime.now(clock).minusSeconds(10),
			LocalDateTime.now(clock).plusDays(1),
			RecruitmentStatus.CLOSED
		));
		RecruitmentEntity waitingRecruitment = recruitmentRepository.save(RecruitmentEntity.create(
			"대기중 모집",
			"설명",
			"공지",
			LocalDateTime.now(clock).plusDays(1),
			LocalDateTime.now(clock).plusDays(2),
			RecruitmentStatus.WAITING
		));

		recruitmentStatusScheduler.synchronizeRecruitmentStatus();

		assertThat(recruitmentRepository.findById(triggeredClosedRecruitment.getId()).orElseThrow().getStatus())
			.isEqualTo(RecruitmentStatus.CLOSED);
		assertThat(recruitmentRepository.findById(waitingRecruitment.getId()).orElseThrow().getStatus())
			.isEqualTo(RecruitmentStatus.WAITING);
	}

	@Test
	void 스케줄러는_시작시각이_되면_대기중_모집을_즉시_연다() {
		recruitment.changeStatus(RecruitmentStatus.WAITING);
		recruitmentRepository.saveAndFlush(recruitment);

		recruitmentStatusScheduler.synchronizeRecruitmentStatus();

		assertThat(recruitmentRepository.findById(recruitment.getId()).orElseThrow().getStatus())
			.isEqualTo(RecruitmentStatus.OPEN);
	}

	@Test
	void 스케줄러는_종료시각이_되면_모집중_모집을_즉시_닫는다() {
		recruitment.update(
			recruitment.getName(),
			recruitment.getDescription(),
			recruitment.getNotice(),
			LocalDateTime.now(clock).minusDays(1),
			LocalDateTime.now(clock).minusSeconds(1),
			RecruitmentStatus.OPEN
		);
		recruitmentRepository.saveAndFlush(recruitment);

		recruitmentStatusScheduler.synchronizeRecruitmentStatus();

		assertThat(recruitmentRepository.findById(recruitment.getId()).orElseThrow().getStatus())
			.isEqualTo(RecruitmentStatus.CLOSED);
	}

	@Test
	void 서로_다른_모집을_동시에_열어도_하나의_모집만_열린다() throws Exception {
		RecruitmentEntity otherRecruitment = recruitmentRepository.save(RecruitmentEntity.create(
			"다른 모집",
			"설명",
			"공지",
			LocalDateTime.now(clock).minusDays(1),
			LocalDateTime.now(clock).plusDays(1),
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

	@Test
	void 전체강좌에_처음_들어가면_학생_타입을_기본으로_선택한다() {
		ParticipantTypeEntity teacherType = participantTypeRepository.save(
			ParticipantTypeEntity.from(ParticipantType.TEACHER)
		);
		recruitmentParticipantTypeRepository.save(
			RecruitmentParticipantTypeEntity.create(recruitment, teacherType, true, true)
		);
		recruitmentQueryService.evictRecruitmentCaches(recruitment.getId());

		LectureCatalogQueryService.LectureCatalogView catalog = lectureCatalogQueryService.findLectureCatalog(
			recruitment,
			null
		);

		assertThat(catalog.selectedParticipantTypeId()).isEqualTo(studentType.getId());
		assertThat(catalog.participantTypes())
			.extracting(LectureCatalogQueryService.ParticipantTypeOption::description)
			.containsExactly("학생", "교사");
	}

	@Test
	void 고정_강좌를_저장하면_타입별_전체강좌에는_지정한_오전오후_강좌만_노출된다() {
		LectureEntity otherMorningLecture = lectureRepository.save(
			LectureEntity.create(recruitment, "다른 오전", "강사", "설명", LectureType.AM, true, 3, 2)
		);
		LectureEntity afternoonLecture = lectureRepository.save(
			LectureEntity.create(recruitment, "고정 오후", "강사", "설명", LectureType.PM, false, 0, 1)
		);
		LectureEntity otherAfternoonLecture = lectureRepository.save(
			LectureEntity.create(recruitment, "다른 오후", "강사", "설명", LectureType.PM, true, 3, 2)
		);

		adminRecruitmentCommandService.updateParticipantTypeRule(
			recruitment.getId(),
			studentRule.getId(),
			false,
			false,
			morningLecture.getId(),
			afternoonLecture.getId()
		);

		assertThat(fixedLectureRepository.findByRecruitmentEntityWithRelations(recruitment))
			.extracting(
				fixedLecture -> fixedLecture.getLectureType(),
				fixedLecture -> fixedLecture.getLectureEntity().getId()
			)
			.containsExactly(
				tuple(LectureType.AM, morningLecture.getId()),
				tuple(LectureType.PM, afternoonLecture.getId())
			);

		LectureCatalogQueryService.LectureCatalogView catalog = lectureCatalogQueryService.findLectureCatalog(
			recruitment,
			studentType.getId()
		);

		assertThat(catalog.fixedLectures()).isTrue();
		assertThat(catalog.lectureSelection().morningLectures())
			.extracting(lecture -> lecture.id())
			.containsExactly(morningLecture.getId())
			.doesNotContain(otherMorningLecture.getId());
		assertThat(catalog.lectureSelection().afternoonLectures())
			.extracting(lecture -> lecture.id())
			.containsExactly(afternoonLecture.getId());

		adminRecruitmentCommandService.updateParticipantTypeRule(
			recruitment.getId(),
			studentRule.getId(),
			false,
			false,
			otherMorningLecture.getId(),
			otherAfternoonLecture.getId()
		);
		LectureCatalogQueryService.LectureCatalogView refreshedCatalog = lectureCatalogQueryService
			.findLectureCatalog(recruitment, studentType.getId());

		assertThat(refreshedCatalog.lectureSelection().morningLectures())
			.extracting(lecture -> lecture.id())
			.containsExactly(otherMorningLecture.getId());
		assertThat(refreshedCatalog.lectureSelection().afternoonLectures())
			.extracting(lecture -> lecture.id())
			.containsExactly(otherAfternoonLecture.getId());
	}

	@Test
	void 직접선택을_다시_허용하면_기존_고정강좌를_제거한다() {
		LectureEntity afternoonLecture = lectureRepository.save(
			LectureEntity.create(recruitment, "고정 오후", "강사", "설명", LectureType.PM, false, 0, 1)
		);
		adminRecruitmentCommandService.updateParticipantTypeRule(
			recruitment.getId(),
			studentRule.getId(),
			false,
			false,
			morningLecture.getId(),
			afternoonLecture.getId()
		);

		adminRecruitmentCommandService.updateParticipantTypeRule(
			recruitment.getId(),
			studentRule.getId(),
			true,
			false,
			null,
			null
		);

		assertThat(fixedLectureRepository.findByRecruitmentEntityWithRelations(recruitment)).isEmpty();
	}

	@Test
	void 고정_강좌는_설정한_시간대와_모집에_속해야한다() {
		LectureEntity afternoonLecture = lectureRepository.save(
			LectureEntity.create(recruitment, "오후", "강사", "설명", LectureType.PM, false, 0, 1)
		);

		assertThatThrownBy(() -> adminRecruitmentCommandService.updateParticipantTypeRule(
			recruitment.getId(),
			studentRule.getId(),
			false,
			false,
			afternoonLecture.getId(),
			morningLecture.getId()
		))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("오전 강의");

		assertThat(fixedLectureRepository.findByRecruitmentEntityWithRelations(recruitment)).isEmpty();
	}

	@Test
	void 고정_강좌로_사용중인_강의는_삭제하거나_시간대를_바꿀_수_없다() {
		LectureEntity afternoonLecture = lectureRepository.save(
			LectureEntity.create(recruitment, "고정 오후", "강사", "설명", LectureType.PM, false, 0, 1)
		);
		adminRecruitmentCommandService.updateParticipantTypeRule(
			recruitment.getId(),
			studentRule.getId(),
			false,
			false,
			morningLecture.getId(),
			afternoonLecture.getId()
		);

		assertThatThrownBy(() -> adminRecruitmentCommandService.deleteLecture(
			recruitment.getId(),
			morningLecture.getId()
		))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("고정 강좌");

		assertThatThrownBy(() -> adminRecruitmentCommandService.updateLecture(
			recruitment.getId(),
			morningLecture.getId(),
			"오전",
			"강사",
			"설명",
			LectureType.PM,
			true,
			3
		))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("고정 강좌");
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
		fixedLectureRepository.deleteAll();
		lectureRepository.deleteAll();
		recruitmentParticipantTypeRepository.deleteAll();
		recruitmentChurchRepository.deleteAll();
		recruitmentRepository.deleteAll();
		participantTypeRepository.deleteAll();
	}
}
