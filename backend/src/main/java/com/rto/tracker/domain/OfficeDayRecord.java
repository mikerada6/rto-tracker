package com.rto.tracker.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "office_day_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfficeDayRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "total_office_time")
    private Long totalOfficeTime;

    @Column(name = "commute_duration")
    private Long commuteDuration;

    @Column(name = "first_office_entry")
    private Instant firstOfficeEntry;

    @Column(name = "last_office_exit")
    private Instant lastOfficeExit;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "office_day_offices_visited",
            joinColumns = @JoinColumn(name = "office_day_record_id"),
            inverseJoinColumns = @JoinColumn(name = "zone_id"))
    @Builder.Default
    private Set<Zone> officesVisited = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
