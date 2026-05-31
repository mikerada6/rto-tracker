package com.rto.tracker.service;

import com.rto.tracker.domain.Zone;
import com.rto.tracker.domain.ZoneType;
import com.rto.tracker.dto.CreateZoneRequest;
import com.rto.tracker.dto.UpdateZoneRequest;
import com.rto.tracker.exception.DuplicateResourceException;
import com.rto.tracker.exception.EntityNotFoundException;
import com.rto.tracker.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZoneService {

    private final ZoneRepository zoneRepository;

    @Transactional
    public Zone createZone(UUID userId, com.rto.tracker.domain.User user, CreateZoneRequest request) {
        if (request.getExternalId() != null) {
            zoneRepository.findByUserIdAndExternalId(userId, request.getExternalId())
                    .ifPresent(z -> {
                        throw new DuplicateResourceException(
                                "externalId '" + request.getExternalId() + "' already exists for this user");
                    });
        }

        Zone zone = Zone.builder()
                .user(user)
                .name(request.getName())
                .type(request.getType())
                .externalId(request.getExternalId())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .radiusMeters(request.getRadiusMeters())
                .build();

        Zone saved = zoneRepository.save(zone);
        log.info("Zone created: id={}, userId={}, name={}, type={}, externalId={}",
                saved.getId(), userId, saved.getName(), saved.getType(), saved.getExternalId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Zone> listZones(UUID userId, ZoneType type, boolean active) {
        if (type != null) {
            return zoneRepository.findByUserIdAndTypeAndActive(userId, type, active);
        }
        return zoneRepository.findByUserIdAndActive(userId, active);
    }

    @Transactional(readOnly = true)
    public Zone getZone(UUID userId, UUID zoneId) {
        return zoneRepository.findByUserIdAndId(userId, zoneId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Zone not found: " + zoneId));
    }

    @Transactional
    public Zone updateZone(UUID userId, UUID zoneId, UpdateZoneRequest request) {
        Zone zone = getZone(userId, zoneId);

        if (request.getName() != null) {
            zone.setName(request.getName());
        }
        if (request.getType() != null) {
            zone.setType(request.getType());
        }
        if (request.getExternalId() != null) {
            // Check uniqueness if changing externalId
            zoneRepository.findByUserIdAndExternalId(userId, request.getExternalId())
                    .filter(z -> !z.getId().equals(zoneId))
                    .ifPresent(z -> {
                        throw new DuplicateResourceException(
                                "externalId '" + request.getExternalId() + "' already exists for this user");
                    });
            zone.setExternalId(request.getExternalId());
        }
        if (request.getLatitude() != null) {
            zone.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            zone.setLongitude(request.getLongitude());
        }
        if (request.getRadiusMeters() != null) {
            zone.setRadiusMeters(request.getRadiusMeters());
        }

        Zone saved = zoneRepository.save(zone);
        log.info("Zone updated: id={}, userId={}", saved.getId(), userId);
        return saved;
    }

    @Transactional
    public void deleteZone(UUID userId, UUID zoneId) {
        Zone zone = getZone(userId, zoneId);
        zone.setActive(false);
        zoneRepository.save(zone);
        log.info("Zone soft-deleted: id={}, userId={}", zoneId, userId);
    }
}
