package com.wemisson.career_camp.common.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.resource.ResourceUrlEncodingFilter;

@Configuration
public class SpringConfiguration {

	public static final ZoneId SERVICE_ZONE_ID = ZoneId.of("Asia/Seoul");

	@Bean
	public Clock serviceClock() {
		return Clock.system(SERVICE_ZONE_ID);
	}

	@Bean
	public ResourceUrlEncodingFilter resourceUrlEncodingFilter() {
		return new ResourceUrlEncodingFilter();
	}
}
