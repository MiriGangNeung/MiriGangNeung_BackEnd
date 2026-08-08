package com.mirigangneung.place.repository;
import com.mirigangneung.place.domain.PlaceImage; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface PlaceImageRepository extends JpaRepository<PlaceImage,UUID> { List<PlaceImage> findByPlaceOrderBySortOrderAsc(com.mirigangneung.place.domain.Place place); }
