package com.mirigangneung.place.repository;

import com.mirigangneung.place.domain.Place;
import com.mirigangneung.place.domain.PlaceImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface PlaceImageRepository extends JpaRepository<PlaceImage, UUID> {
    List<PlaceImage> findByPlaceOrderBySortOrderAsc(Place place);

    List<PlaceImage> findByPlaceInOrderBySortOrderAsc(List<Place> places);

    @Transactional
    void deleteByPlace(Place place);

    void deleteByPlaceIn(List<Place> places);
}
