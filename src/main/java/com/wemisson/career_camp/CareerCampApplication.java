package com.wemisson.career_camp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CareerCampApplication {

	public static void main(String[] args) {
		SpringApplication.run(CareerCampApplication.class, args);
	}

}
