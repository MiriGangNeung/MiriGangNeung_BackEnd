package com.mirigangneung.place.service;

import com.mirigangneung.common.error.ApiException; import com.mirigangneung.infrastructure.tourapi.TourApiClient; import com.mirigangneung.place.domain.*; import com.mirigangneung.place.dto.*; import com.mirigangneung.place.repository.*; import org.springframework.data.domain.*; import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.util.*;

@Service public class PlaceService {
 private final PlaceRepository places; private final PlaceImageRepository images; private final TourApiClient tour;
 public PlaceService(PlaceRepository p,PlaceImageRepository i,TourApiClient t){places=p;images=i;tour=t;}
 @Transactional public PlacePageResponse search(String category,String keyword,int page,int size){ List<TourApiClient.TourPlace> remote=tour.search(keyword,category,page,size); remote.forEach(this::upsert); Pageable pageable=PageRequest.of(page,size); Page<Place> result;
  if(category!=null&&!category.isBlank()) result=places.findByCategoryContainingAndNameContaining(category,keyword==null?"":keyword,pageable); else result=places.findByRegionContainingAndNameContaining("강릉",keyword==null?"":keyword,pageable); return PlacePageResponse.from(result); }
 @Transactional public PlaceDetailResponse detail(String id){Place p=existingOrFetch(id);return PlaceDetailResponse.from(p,images.findByPlaceOrderBySortOrderAsc(p).stream().map(PlaceImage::getImageUrl).toList());}
 @Transactional public List<PlaceResponse> candidates(String id,boolean related){Place p=find(id); List<TourApiClient.TourPlace> rs=related?tour.related(p.getTourContentId()):(p.getLatitude()==null||p.getLongitude()==null?List.of():tour.nearby(p.getTourContentId(),p.getLatitude(),p.getLongitude())); return rs.stream().map(this::upsert).filter(Objects::nonNull).map(PlaceResponse::from).toList();}
 @Transactional public Place find(String id){try{return places.findById(UUID.fromString(id)).orElseThrow(()->notFound());}catch(IllegalArgumentException e){return places.findByTourContentId(id).orElseThrow(()->notFound());}}
 private Place existingOrFetch(String id){try{return find(id);}catch(ApiException e){if(!"PLACE_NOT_FOUND".equals(e.getCode()))throw e;return tour.find(id).map(this::upsert).orElseThrow(this::notFound);}}
 private ApiException notFound(){return new ApiException("PLACE_NOT_FOUND",HttpStatus.NOT_FOUND,"관광지를 찾을 수 없습니다.");}
 private Place upsert(TourApiClient.TourPlace t){ if(t.contentId()==null||t.name()==null)return null; Place p=places.findByTourContentId(t.contentId()).orElseGet(()->new Place(t.contentId(),t.name(),t.region(),t.category(),t.description(),t.latitude(),t.longitude(),t.thumbnailUrl(),"KTO")); p.updateFrom(new Place(t.contentId(),t.name(),t.region(),t.category(),t.description(),t.latitude(),t.longitude(),t.thumbnailUrl(),"KTO")); Place saved=places.save(p); if(t.imageUrls()!=null) {int n=0;for(String url:t.imageUrls())images.save(new PlaceImage(saved,url,null,"KTO",n++));} return saved; }
}
