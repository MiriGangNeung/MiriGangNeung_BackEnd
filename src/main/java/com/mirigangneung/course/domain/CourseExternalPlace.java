package com.mirigangneung.course.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A copy of an external place attached to one course.
 *
 * The snapshot deliberately stores the provider response at the time the
 * user adds the place so that a course remains readable after Kakao changes
 * or removes the original listing.
 */
@Entity
@Table(name = "course_external_places", indexes = {
        @Index(name = "idx_course_external_place_provider_id", columnList = "source,externalPlaceId")
})
public class CourseExternalPlace {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(nullable = false, length = 128)
    private String externalPlaceId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 500)
    private String categoryName;

    @Column(nullable = false, length = 32)
    private String category;

    @Column(length = 500)
    private String address;

    @Column(length = 500)
    private String roadAddress;

    @Column(length = 100)
    private String phone;

    @Column(length = 2048)
    private String placeUrl;

    private Double latitude;
    private Double longitude;

    protected CourseExternalPlace() {
    }

    public CourseExternalPlace(
            String source,
            String externalPlaceId,
            String name,
            String categoryName,
            String category,
            String address,
            String roadAddress,
            String phone,
            String placeUrl,
            Double latitude,
            Double longitude
    ) {
        this.source = source;
        this.externalPlaceId = externalPlaceId;
        this.name = name;
        this.categoryName = categoryName;
        this.category = category;
        this.address = defaultString(address);
        this.roadAddress = defaultString(roadAddress);
        this.phone = defaultString(phone);
        this.placeUrl = defaultString(placeUrl);
        this.latitude = latitude;
        this.longitude = longitude;
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    public UUID getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public String getExternalPlaceId() {
        return externalPlaceId;
    }

    public String getName() {
        return name;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getCategory() {
        return category;
    }

    public String getAddress() {
        return address;
    }

    public String getRoadAddress() {
        return roadAddress;
    }

    public String getPhone() {
        return phone;
    }

    public String getPlaceUrl() {
        return placeUrl;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }
}
