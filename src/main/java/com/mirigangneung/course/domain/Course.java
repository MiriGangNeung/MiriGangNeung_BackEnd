package com.mirigangneung.course.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "courses")
public class Course {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String title;
    private String durationType;
    private LocalDate startDate;
    private LocalDate endDate;

    @Column(name = "travel_types", length = 100)
    private String travelTypes;

    @Column(name = "detail_types", length = 160)
    private String detailTypes;

    @Column(length = 32)
    private String companion;

    private String shareTokenHash;
    private OffsetDateTime shareExpiresAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime expiresAt;

    protected Course() {
    }

    public Course(String duration, LocalDate start, LocalDate end) {
        this(duration, start, end, List.of(), List.of(), "");
    }

    public Course(
            String duration,
            LocalDate start,
            LocalDate end,
            List<String> travelTypes,
            String companion
    ) {
        this(duration, start, end, travelTypes, List.of(), companion);
    }

    public Course(
            String duration,
            LocalDate start,
            LocalDate end,
            List<String> travelTypes,
            List<String> detailTypes,
            String companion
    ) {
        durationType = duration;
        startDate = start;
        endDate = end;
        title = "나만의 강릉 코스";
        this.travelTypes = serializeTravelTypes(travelTypes);
        this.detailTypes = serializeTravelTypes(detailTypes);
        this.companion = companion == null ? "" : companion.trim();
        createdAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDurationType() {
        return durationType;
    }

    public List<String> getTravelTypes() {
        return deserializeTravelTypes(travelTypes);
    }

    public List<String> getDetailTypes() {
        return deserializeTravelTypes(detailTypes);
    }

    public String getCompanion() {
        return companion == null ? "" : companion;
    }

    public String getShareTokenHash() {
        return shareTokenHash;
    }

    public OffsetDateTime getShareExpiresAt() {
        return shareExpiresAt;
    }

    public void share(String hash, OffsetDateTime expires) {
        shareTokenHash = hash;
        shareExpiresAt = expires;
    }

    public void revokeShare() {
        shareTokenHash = null;
        shareExpiresAt = null;
    }

    private static String serializeTravelTypes(List<String> types) {
        if (types == null) {
            return "";
        }
        return types.stream()
                .filter(type -> type != null && !type.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(","));
    }

    private static List<String> deserializeTravelTypes(String serializedTypes) {
        if (serializedTypes == null || serializedTypes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(serializedTypes.split(","))
                .map(String::trim)
                .filter(type -> !type.isBlank())
                .toList();
    }
}
