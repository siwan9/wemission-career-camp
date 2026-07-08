package com.wemisson.career_camp.domain.participant.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wemisson.career_camp.domain.participant.entity.ParticipantTypeEntity;

public interface ParticipantTypeRepository extends JpaRepository<ParticipantTypeEntity, Long> {
}
