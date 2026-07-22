package com.wemisson.career_camp.domain.participant.service.command;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wemisson.career_camp.domain.participant.service.draft.LectureCapacityService;
import com.wemisson.career_camp.domain.participant.service.draft.LectureDraftService;
import com.wemisson.career_camp.domain.recruitment.repository.LectureRepository;
import com.wemisson.career_camp.domain.recruitment.service.query.RecruitmentQueryService;

@ExtendWith(MockitoExtension.class)
class LectureApplicationServiceTest {

	@Mock
	private LectureRepository lectureRepository;
	@Mock
	private RecruitmentQueryService recruitmentService;
	@Mock
	private LectureCapacityService lectureCapacityService;
	@Mock
	private LectureDraftService lectureDraftService;
	@Mock
	private LectureApplicationFinalizer lectureApplicationFinalizer;
	@Mock
	private Clock clock;
	@InjectMocks
	private LectureApplicationService lectureApplicationService;

	@Test
	void 임시점유_토큰이_없으면_해제_트랜잭션을_호출하지_않는다() {
		lectureApplicationService.releaseDrafts(null);
		lectureApplicationService.releaseDrafts("  ");

		verifyNoInteractions(lectureDraftService);
	}

	@Test
	void 임시점유_토큰이_있으면_해제_트랜잭션을_호출한다() {
		lectureApplicationService.releaseDrafts("draft-token");

		verify(lectureDraftService).releaseAll("draft-token");
	}
}
