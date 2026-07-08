package com.wemisson.career_camp.domain.participant.service;

import org.springframework.stereotype.Service;

import com.wemisson.career_camp.domain.participant.dto.ParticipantCreateRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RegistrationService {

	// private final RegistrationRepository participantRepository;

	public void register(ParticipantCreateRequest request) {
		// participantRepository.save(participant);
	}
}
