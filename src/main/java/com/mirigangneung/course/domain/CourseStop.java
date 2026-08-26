package com.mirigangneung.course.domain;

import com.mirigangneung.place.domain.Place;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "course_stops")
public class CourseStop {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    private Place place;

    @ManyToOne(fetch = FetchType.LAZY)
    private CourseExternalPlace externalPlace;

    private int sequence;
    private String arrivalTime;
    private int stayMinutes;
    private String crowdLevel;
    private String note;
    private boolean isOnePick;
    private Double latitudeSnapshot;
    private Double longitudeSnapshot;

    protected CourseStop() {
    }

    public CourseStop(Course course, Place place, int sequence, boolean onePick) {
        this.course = course;
        this.place = place;
        this.sequence = sequence;
        this.isOnePick = onePick;
        initializeDefaults(onePick ? "원픽 장소" : "선택한 여행 유형과 가까운 추천 장소");
        this.latitudeSnapshot = place.getLatitude();
        this.longitudeSnapshot = place.getLongitude();
    }

    public CourseStop(Course course, CourseExternalPlace externalPlace, int sequence, boolean onePick) {
        this.course = course;
        this.externalPlace = externalPlace;
        this.sequence = sequence;
        this.isOnePick = onePick;
        initializeDefaults("코스에 추가한 주변 장소");
        this.latitudeSnapshot = externalPlace.getLatitude();
        this.longitudeSnapshot = externalPlace.getLongitude();
    }

    private void initializeDefaults(String note) {
        this.stayMinutes = 60;
        this.arrivalTime = "09:00";
        this.crowdLevel = "LOW";
        this.note = note;
    }

    public UUID getId() {
        return id;
    }

    public int getSequence() {
        return sequence;
    }

    public void changeSequence(int sequence) {
        this.sequence = sequence;
    }

    public Course getCourse() {
        return course;
    }

    public Place getPlace() {
        return place;
    }

    public CourseExternalPlace getExternalPlace() {
        return externalPlace;
    }

    public boolean isExternal() {
        return externalPlace != null;
    }

    public String getPlaceId() {
        return place == null ? null : place.getId().toString();
    }

    public String getExternalPlaceId() {
        return externalPlace == null ? null : externalPlace.getExternalPlaceId();
    }

    public String getDisplayName() {
        return place != null ? place.getName() : externalPlace.getName();
    }

    public String getThumbnailUrl() {
        return place == null ? null : place.getThumbnailUrl();
    }

    public String getCategory() {
        return place != null ? place.getCategory() : externalPlace.getCategory();
    }

    public String getCategoryName() {
        return externalPlace == null ? null : externalPlace.getCategoryName();
    }

    public String getAddress() {
        if (place != null) {
            return place.getRegion();
        }
        return externalPlace.getRoadAddress().isBlank()
                ? externalPlace.getAddress()
                : externalPlace.getRoadAddress();
    }

    public String getPhone() {
        return externalPlace == null ? null : externalPlace.getPhone();
    }

    public String getPlaceUrl() {
        return externalPlace == null ? null : externalPlace.getPlaceUrl();
    }

    public String getArrivalTime() {
        return arrivalTime;
    }

    public int getStayMinutes() {
        return stayMinutes;
    }

    public String getCrowdLevel() {
        return crowdLevel;
    }

    public String getNote() {
        return note;
    }

    public boolean isOnePick() {
        return isOnePick;
    }

    public Double getLatitudeSnapshot() {
        return latitudeSnapshot;
    }

    public Double getLongitudeSnapshot() {
        return longitudeSnapshot;
    }

    public Double getLatitude() {
        return latitudeSnapshot;
    }

    public Double getLongitude() {
        return longitudeSnapshot;
    }
}
