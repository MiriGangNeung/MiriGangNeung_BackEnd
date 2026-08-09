package com.mirigangneung.place.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "places", indexes = @Index(name = "idx_place_tour_id", columnList = "tour_content_id", unique = true))
public class Place {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tour_content_id", nullable = false)
    private String tourContentId;

    @Column(nullable = false)
    private String name;

    private String region;
    private String category;

    @Column(length = 4000)
    private String description;

    private Double latitude;
    private Double longitude;
    private String thumbnailUrl;
    private String source;
    private OffsetDateTime sourceUpdatedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    protected Place() {
    }

    public Place(String contentId, String name, String region, String category, String description,
                 Double lat, Double lon, String thumb, String source) {
        this.tourContentId = contentId;
        this.name = name;
        this.region = region;
        this.category = category;
        this.description = description;
        this.latitude = lat;
        this.longitude = lon;
        this.thumbnailUrl = thumb;
        this.source = source;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTourContentId() {
        return tourContentId;
    }

    public String getName() {
        return name;
    }

    public String getRegion() {
        return region;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public OffsetDateTime getSourceUpdatedAt() {
        return sourceUpdatedAt;
    }

    public void updateFrom(Place place) {
        updateFrom(place, OffsetDateTime.now());
    }

    public void updateFrom(Place place, OffsetDateTime sourceUpdatedAt) {
        name = place.name;
        region = place.region;
        category = place.category;
        description = place.description;
        latitude = place.latitude;
        longitude = place.longitude;
        thumbnailUrl = place.thumbnailUrl;
        this.sourceUpdatedAt = sourceUpdatedAt == null ? OffsetDateTime.now() : sourceUpdatedAt;
    }
}
