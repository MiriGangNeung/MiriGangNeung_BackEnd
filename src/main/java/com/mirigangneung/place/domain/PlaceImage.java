package com.mirigangneung.place.domain;
import jakarta.persistence.*; import java.util.*;
@Entity @Table(name="place_images") public class PlaceImage {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id; @ManyToOne(fetch=FetchType.LAZY,optional=false) private Place place; private String imageUrl; private String title; private String source; private Integer sortOrder;
 protected PlaceImage() {} public PlaceImage(Place p,String url,String title,String source,int order){this.place=p;this.imageUrl=url;this.title=title;this.source=source;this.sortOrder=order;}
 public String getImageUrl(){return imageUrl;} public String getTitle(){return title;} public Integer getSortOrder(){return sortOrder;}
}
