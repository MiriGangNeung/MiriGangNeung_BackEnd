package com.mirigangneung.place.repository;

import com.mirigangneung.place.domain.Place;
import com.mirigangneung.place.domain.PlaceImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlaceImageRepository extends JpaRepository<PlaceImage, UUID> {
    List<PlaceImage> findByPlaceOrderBySortOrderAsc(Place place);

    void deleteByPlace(Place place);
}
