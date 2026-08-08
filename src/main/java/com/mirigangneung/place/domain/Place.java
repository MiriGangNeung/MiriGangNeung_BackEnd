package com.mirigangneung.place.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity @Table(name="places", indexes=@Index(name="idx_place_tour_id", columnList="tour_content_id", unique=true))
public class Place {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tour_content_id", nullable=false) private String tourContentId;
    @Column(nullable=false) private String name;
    private String region; private String category;
    @Column(length=4000) private String description;
    private Double latitude; private Double longitude; private String thumbnailUrl; private String source;
    private OffsetDateTime sourceUpdatedAt; private OffsetDateTime createdAt; private OffsetDateTime updatedAt;
    protected Place() {}
    public Place(String contentId,String name,String region,String category,String description,Double lat,Double lon,String thumb,String source) { this.tourContentId=contentId;this.name=name;this.region=region;this.category=category;this.description=description;this.latitude=lat;this.longitude=lon;this.thumbnailUrl=thumb;this.source=source;this.createdAt=OffsetDateTime.now();this.updatedAt=this.createdAt; }
    @PreUpdate void touch(){updatedAt=OffsetDateTime.now();}
    public UUID getId(){return id;} public String getTourContentId(){return tourContentId;} public String getName(){return name;} public String getRegion(){return region;} public String getCategory(){return category;} public String getDescription(){return description;} public Double getLatitude(){return latitude;} public Double getLongitude(){return longitude;} public String getThumbnailUrl(){return thumbnailUrl;}
    public void updateFrom(Place p){name=p.name;region=p.region;category=p.category;description=p.description;latitude=p.latitude;longitude=p.longitude;thumbnailUrl=p.thumbnailUrl;sourceUpdatedAt=OffsetDateTime.now();}
}
