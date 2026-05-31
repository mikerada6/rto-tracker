package com.rto.tracker.service;

import com.rto.tracker.domain.User;
import com.rto.tracker.domain.Zone;
import com.rto.tracker.domain.ZoneType;
import com.rto.tracker.dto.CreateZoneRequest;
import com.rto.tracker.exception.DuplicateResourceException;
import com.rto.tracker.exception.EntityNotFoundException;
import com.rto.tracker.repository.ZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ZoneServiceTest {

    @Mock
    private ZoneRepository zoneRepository;

    private ZoneService zoneService;
    private User testUser;

    @BeforeEach
    void setUp() {
        zoneService = new ZoneService(zoneRepository);
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .displayName("Test User")
                .apiKeyHash("hash")
                .build();
    }

    @Test
    void createZone_success() {
        CreateZoneRequest request = CreateZoneRequest.builder()
                .name("City Office")
                .type(ZoneType.OFFICE)
                .externalId("zone.city_office")
                .build();

        Zone saved = Zone.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .name("City Office")
                .type(ZoneType.OFFICE)
                .externalId("zone.city_office")
                .build();

        when(zoneRepository.findByUserIdAndExternalId(testUser.getId(), "zone.city_office"))
                .thenReturn(Optional.empty());
        when(zoneRepository.save(any(Zone.class))).thenReturn(saved);

        Zone result = zoneService.createZone(testUser.getId(), testUser, request);

        assertThat(result.getName()).isEqualTo("City Office");
        assertThat(result.getType()).isEqualTo(ZoneType.OFFICE);
        verify(zoneRepository).save(any(Zone.class));
    }

    @Test
    void createZone_duplicateExternalId_throws() {
        CreateZoneRequest request = CreateZoneRequest.builder()
                .name("City Office")
                .type(ZoneType.OFFICE)
                .externalId("zone.city_office")
                .build();

        when(zoneRepository.findByUserIdAndExternalId(testUser.getId(), "zone.city_office"))
                .thenReturn(Optional.of(Zone.builder().id(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> zoneService.createZone(testUser.getId(), testUser, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("zone.city_office");
    }

    @Test
    void deleteZone_setsActiveToFalse() {
        UUID zoneId = UUID.randomUUID();
        Zone zone = Zone.builder()
                .id(zoneId)
                .user(testUser)
                .name("Office")
                .type(ZoneType.OFFICE)
                .active(true)
                .build();

        when(zoneRepository.findByUserIdAndId(testUser.getId(), zoneId))
                .thenReturn(Optional.of(zone));
        when(zoneRepository.save(any(Zone.class))).thenReturn(zone);

        zoneService.deleteZone(testUser.getId(), zoneId);

        assertThat(zone.isActive()).isFalse();
        verify(zoneRepository).save(zone);
    }

    @Test
    void getZone_notOwned_throwsNotFound() {
        UUID zoneId = UUID.randomUUID();
        when(zoneRepository.findByUserIdAndId(testUser.getId(), zoneId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> zoneService.getZone(testUser.getId(), zoneId))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
