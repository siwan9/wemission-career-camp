package com.wemisson.career_camp.domain.recruitment.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.wemisson.career_camp.common.transaction.AfterCommitExecutor;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentChurchRepository;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentParticipantTypeRepository;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentRepository;

class RecruitmentQueryServiceTest {

	@Test
	void 참가자_타입_캐시를_동시에_조회해도_DB는_한_번만_조회한다() throws Exception {
		RecruitmentRepository recruitmentRepository = mock(RecruitmentRepository.class);
		RecruitmentParticipantTypeRepository participantTypeRepository = mock(
			RecruitmentParticipantTypeRepository.class
		);
		RecruitmentChurchRepository churchRepository = mock(RecruitmentChurchRepository.class);
		AfterCommitExecutor afterCommitExecutor = mock(AfterCommitExecutor.class);
		RecruitmentEntity recruitment = mock(RecruitmentEntity.class);
		RecruitmentQueryService service = new RecruitmentQueryService(
			recruitmentRepository,
			participantTypeRepository,
			churchRepository,
			afterCommitExecutor
		);
		int requestCount = 30;
		CountDownLatch ready = new CountDownLatch(requestCount);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(requestCount);
		List<Future<List<RecruitmentQueryService.ParticipantTypeRule>>> futures = new java.util.ArrayList<>();

		when(recruitment.getId()).thenReturn(1L);
		when(participantTypeRepository.findByRecruitmentEntityOrderByIdAsc(recruitment)).thenAnswer(invocation -> {
			Thread.sleep(30);
			return List.of();
		});

		try {
			for (int index = 0; index < requestCount; index++) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return service.findParticipantTypeRules(recruitment);
				}));
			}

			ready.await();
			start.countDown();

			for (Future<List<RecruitmentQueryService.ParticipantTypeRule>> future : futures) {
				assertThat(future.get()).isEmpty();
			}
		} finally {
			executor.shutdownNow();
		}

		verify(participantTypeRepository).findByRecruitmentEntityOrderByIdAsc(recruitment);
	}
}
