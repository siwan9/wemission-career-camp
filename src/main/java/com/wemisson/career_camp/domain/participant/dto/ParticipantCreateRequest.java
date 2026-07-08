package com.wemisson.career_camp.domain.participant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ParticipantCreateRequest(
	@NotBlank
	String name,
	@NotNull
	Long participantTypeId,
	@NotNull
	Long churchId,
	@NotBlank
	@Pattern(regexp = "\\d{10,11}")
	String phoneNumber,
	@NotBlank
	@Pattern(regexp = "\\d{6}")
	String password
) {
	public static ParticipantCreateRequest createEmpty() {
		return new ParticipantCreateRequest(
			null,
			null,
			null,
			null,
			null
		);
	}
}
