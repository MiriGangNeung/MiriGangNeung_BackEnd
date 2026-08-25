package com.mirigangneung.place.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "place_images")
public class PlaceImage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Place place;

    private String imageUrl;
    private String title;
    private String source;
    private Integer sortOrder;
    private String copyrightCode;
    private String originalStorageKey;
    private String thumbnailStorageKey;
    private String contentType;
    private Long originalByteSize;
    private Long thumbnailByteSize;

    protected PlaceImage() {
    }

    public PlaceImage(Place place, String url, String title, String source, int order) {
        this(place, url, title, source, order, null);
    }

    public PlaceImage(Place place, String url, String title, String source, int order, String copyrightCode) {
        this(place, url, title, source, order, copyrightCode, null, null, null, null, null);
    }

    public PlaceImage(
            Place place,
            String url,
            String title,
            String source,
            int order,
            String copyrightCode,
            String originalStorageKey,
            String thumbnailStorageKey,
            String contentType,
            Long originalByteSize,
            Long thumbnailByteSize) {
        this.place = place;
        this.imageUrl = url;
        this.title = title;
        this.source = source;
        this.sortOrder = order;
        this.copyrightCode = copyrightCode;
        this.originalStorageKey = originalStorageKey;
        this.thumbnailStorageKey = thumbnailStorageKey;
        this.contentType = contentType;
        this.originalByteSize = originalByteSize;
        this.thumbnailByteSize = thumbnailByteSize;
    }

    public Place getPlace() {
        return place;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getTitle() {
        return title;
    }

    public String getSource() {
        return source;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public String getCopyrightCode() {
        return copyrightCode;
    }

    public String getOriginalStorageKey() {
        return originalStorageKey;
    }

    public String getThumbnailStorageKey() {
        return thumbnailStorageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getOriginalByteSize() {
        return originalByteSize;
    }

    public Long getThumbnailByteSize() {
        return thumbnailByteSize;
    }
}
