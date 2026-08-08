package com.mirigangneung.place.dto;
import com.mirigangneung.place.domain.Place; import java.util.*;
public record PlaceResponse(String id,String name,String region,String category,List<String> tags,String thumbnailUrl,Double latitude,Double longitude) {
 public static PlaceResponse from(Place p){return new PlaceResponse(p.getId().toString(),p.getName(),p.getRegion(),p.getCategory(),List.of(),p.getThumbnailUrl(),p.getLatitude(),p.getLongitude());}
}
