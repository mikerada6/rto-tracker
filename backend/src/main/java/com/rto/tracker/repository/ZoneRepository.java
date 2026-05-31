package com.rto.tracker.repository;

import com.rto.tracker.domain.Zone;
import com.rto.tracker.domain.ZoneType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ZoneRepository extends JpaRepository<Zone, UUID> {

    List<Zone> findByUserIdAndActive(UUID userId, boolean active);

    List<Zone> findByUserIdAndTypeAndActive(UUID userId, ZoneType type, boolean active);

    Optional<Zone> findByUserIdAndId(UUID userId, UUID zoneId);

    Optional<Zone> findByUserIdAndExternalId(UUID userId, String externalId);
}
