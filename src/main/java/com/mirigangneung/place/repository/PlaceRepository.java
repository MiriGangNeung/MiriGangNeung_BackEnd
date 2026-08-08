package com.mirigangneung.place.repository;
import com.mirigangneung.place.domain.Place; import org.springframework.data.domain.*; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface PlaceRepository extends JpaRepository<Place,UUID> { Optional<Place> findByTourContentId(String id); Page<Place> findByRegionContainingAndNameContaining(String region,String name,Pageable p); Page<Place> findByCategoryContainingAndNameContaining(String category,String name,Pageable p); }
