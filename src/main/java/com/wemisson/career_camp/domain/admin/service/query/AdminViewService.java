package com.wemisson.career_camp.domain.admin.service.query;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wemisson.career_camp.domain.admin.dto.AdminParticipantResult;
import com.wemisson.career_camp.domain.participant.entity.ParticipantEntity;
import com.wemisson.career_camp.domain.participant.entity.ParticipantLectureEntity;
import com.wemisson.career_camp.domain.participant.repository.ParticipantLectureRepository;
import com.wemisson.career_camp.domain.participant.repository.ParticipantRepository;
import com.wemisson.career_camp.domain.recruitment.dto.RecruitmentStatus;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentEntity;
import com.wemisson.career_camp.domain.recruitment.repository.LectureRepository;
import com.wemisson.career_camp.domain.recruitment.repository.RecruitmentRepository;
import com.wemisson.career_camp.domain.recruitment.service.query.LectureQueryService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminViewService {

	private final RecruitmentRepository recruitmentRepository;
	private final ParticipantRepository participantRepository;
	private final ParticipantLectureRepository participantLectureRepository;
	private final LectureRepository lectureRepository;
	private final LectureQueryService lectureService;

	@Transactional(readOnly = true)
	public AdminHomeView findAdminHomeView() {
		List<RecruitmentEntity> recruitments = recruitmentRepository.findAllByOrderByIdDesc();
		Map<Long, Integer> participantCounts = findParticipantCountsByRecruitmentId(recruitments);

		List<RecruitmentEntity> openRecruitments = recruitments.stream()
			.filter(RecruitmentEntity::isOpen)
			.toList();
		List<RecruitmentEntity> waitingRecruitments = recruitments.stream()
			.filter(RecruitmentEntity::isWaiting)
			.toList();
		List<RecruitmentEntity> closedRecruitments = recruitments.stream()
			.filter(RecruitmentEntity::isClosed)
			.toList();
		return new AdminHomeView(
			findCurrentStatusDescription(openRecruitments, waitingRecruitments),
			List.of(
				new RecruitmentGroup(
					"모집중",
					"신청자가 접수할 수 있는 현재 모집입니다.",
					RecruitmentStatus.OPEN,
					openRecruitments,
					true
				),
				new RecruitmentGroup(
					"대기중",
					"신청 전 강좌 목록만 공개되는 모집입니다.",
					RecruitmentStatus.WAITING,
					waitingRecruitments,
					true
				),
				new RecruitmentGroup(
					"지난 모집",
					"모집중 또는 대기중 모집이 없을 때 가장 최근 종료 모집의 강좌와 신청 내역이 사용자에게 공개됩니다.",
					RecruitmentStatus.CLOSED,
					closedRecruitments,
					false
				)
			),
			participantCounts,
			recruitments.size()
		);
	}

	private String findCurrentStatusDescription(
		List<RecruitmentEntity> openRecruitments,
		List<RecruitmentEntity> waitingRecruitments
	) {
		if (!openRecruitments.isEmpty()) {
			return RecruitmentStatus.OPEN.getDescription();
		}
		if (!waitingRecruitments.isEmpty()) {
			return RecruitmentStatus.WAITING.getDescription();
		}

		return RecruitmentStatus.CLOSED.getDescription();
	}

	private Map<Long, Integer> findParticipantCountsByRecruitmentId(List<RecruitmentEntity> recruitments) {
		if (recruitments.isEmpty()) {
			return Map.of();
		}

		Map<Long, Integer> countedRecruitments = participantRepository.countByRecruitmentEntities(recruitments)
			.stream()
			.collect(Collectors.toMap(
				ParticipantRepository.RecruitmentParticipantCount::getRecruitmentId,
				count -> count.getParticipantCount().intValue()
			));

		return recruitments.stream()
			.collect(Collectors.toMap(
				RecruitmentEntity::getId,
				recruitment -> countedRecruitments.getOrDefault(recruitment.getId(), 0)
			));
	}

	@Transactional(readOnly = true)
	public AdminRecruitmentDashboardView findRecruitmentDashboardView(
		Long recruitmentId,
		String phoneNumber
	) {
		RecruitmentEntity recruitmentEntity = findRecruitment(recruitmentId);

		return new AdminRecruitmentDashboardView(
			recruitmentEntity,
			getNextRecruitmentStatusDescription(recruitmentEntity),
			participantRepository.countByRecruitmentEntity(recruitmentEntity),
			participantLectureRepository.countMorningApplications(recruitmentEntity),
			participantLectureRepository.countAfternoonApplications(recruitmentEntity),
			lectureRepository.countByRecruitmentEntity(recruitmentEntity),
			lectureService.findLectures(recruitmentEntity, true, true),
			searchParticipants(recruitmentEntity, phoneNumber)
		);
	}

	private List<AdminParticipantResult> searchParticipants(
		RecruitmentEntity recruitmentEntity,
		String phoneNumber
	) {
		if (phoneNumber == null || phoneNumber.isBlank()) {
			return List.of();
		}

		List<ParticipantEntity> participants = participantRepository
			.findByRecruitmentEntityAndPhoneNumberContainingOrderByNameAsc(
				recruitmentEntity,
				phoneNumber.replaceAll("\\D", "")
			);
		Map<Long, ParticipantLectureEntity> lectureByParticipantId = participantLectureRepository
			.findByParticipantEntityIn(participants)
			.stream()
			.collect(Collectors.toMap(
				participantLecture -> participantLecture.getParticipantEntity().getId(),
				Function.identity()
			));

		return participants.stream()
			.map(participant -> AdminParticipantResult.of(
				participant,
				lectureByParticipantId.get(participant.getId())
			))
			.toList();
	}

	private RecruitmentEntity findRecruitment(Long recruitmentId) {
		return recruitmentRepository.findById(recruitmentId)
			.orElseThrow(() -> new IllegalArgumentException("모집을 찾을 수 없습니다."));
	}

	private String getNextRecruitmentStatusDescription(RecruitmentEntity recruitmentEntity) {
		if (recruitmentEntity.isClosed()) {
			return "대기중";
		}
		if (recruitmentEntity.isWaiting()) {
			return "모집시작";
		}

		return "종료";
	}

	public record AdminHomeView(
		String currentStatusDescription,
		List<RecruitmentGroup> recruitmentGroups,
		Map<Long, Integer> participantCounts,
		int totalRecruitmentCount
	) {
	}

	public record RecruitmentGroup(
		String title,
		String description,
		RecruitmentStatus status,
		List<RecruitmentEntity> recruitments,
		boolean active
	) {
	}

	public record AdminRecruitmentDashboardView(
		RecruitmentEntity recruitment,
		String nextRecruitmentStatusDescription,
		int participantCount,
		int morningApplicationCount,
		int afternoonApplicationCount,
		long lectureCount,
		LectureQueryService.LectureSelection lectureSelection,
		List<AdminParticipantResult> quickResults
	) {
	}
}
