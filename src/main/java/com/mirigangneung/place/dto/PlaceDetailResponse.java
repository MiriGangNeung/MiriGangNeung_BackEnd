package com.mirigangneung.place.dto;
import com.mirigangneung.place.domain.Place; import java.util.*;
public record PlaceDetailResponse(String id,String name,String region,String category,String description,List<String> imageUrls,Double latitude,Double longitude){ public static PlaceDetailResponse from(Place p,List<String> images){return new PlaceDetailResponse(p.getId().toString(),p.getName(),p.getRegion(),p.getCategory(),p.getDescription(),images,p.getLatitude(),p.getLongitude());}}
