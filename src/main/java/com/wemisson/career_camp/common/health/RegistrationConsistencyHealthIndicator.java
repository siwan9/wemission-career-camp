package com.wemisson.career_camp.common.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RegistrationConsistencyHealthIndicator implements HealthIndicator {

	private final JdbcTemplate jdbcTemplate;

	@Override
	public Health health() {
		Integer mismatchCount = jdbcTemplate.queryForObject(
			"""
			select count(*)
			from lectures lecture
			where lecture.participant_count <>
				(select count(*) from participant_lectures application
				 where application.morning_lecture_id = lecture.id)
				+
				(select count(*) from participant_lectures application
				 where application.afternoon_lecture_id = lecture.id)
			""",
			Integer.class
		);

		if (mismatchCount == null || mismatchCount == 0) {
			return Health.up().build();
		}

		return Health.down()
			.withDetail("lectureParticipantCountMismatches", mismatchCount)
			.build();
	}
}
