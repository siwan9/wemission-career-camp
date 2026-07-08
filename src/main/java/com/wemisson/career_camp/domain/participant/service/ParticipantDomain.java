package com.wemisson.career_camp.domain.participant.service;

import java.util.regex.Pattern;

import com.wemisson.career_camp.domain.participant.dto.ParticipantType;

import lombok.Getter;

@Getter
public class ParticipantDomain {
	private final String name;
	private final ParticipantType participantType;
	private final String churchName;
	private final String phoneNumber;
	private final String password;

	private static final Pattern PASSWORD_PATTERN = Pattern.compile("^\\d{6}$");
	private static final Pattern PHONE_NUMBER_PATTERN = Pattern.compile("^\\d+$");


	public ParticipantDomain(String name, ParticipantType participantType, String churchName, String phoneNumber,
		String password) {
		validate();
		this.name = name;
		this.participantType = participantType;
		this.churchName = churchName;
		this.phoneNumber = phoneNumber;
		this.password = password;
	}
	private void validate() {
		if (!PHONE_NUMBER_PATTERN.matcher(phoneNumber).matches()) {
			throw new IllegalArgumentException("휴대폰 번호는 숫자만 입력해야 합니다.");
		}
		if (!PASSWORD_PATTERN.matcher(password).matches()) {
			throw new IllegalArgumentException("비밀번호는 6자리 숫자여야 합니다.");
		}
	}
}
