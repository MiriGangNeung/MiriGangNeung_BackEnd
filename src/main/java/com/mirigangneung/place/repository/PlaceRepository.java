package com.mirigangneung.place.repository;

import com.mirigangneung.place.domain.Place;
import com.mirigangneung.place.service.PromptPlaceCatalog;
import java.util.Collection;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRepository extends JpaRepository<Place, UUID> {
    Optional<Place> findByTourContentId(String id);

    List<Place> findAllByTourContentIdIn(Collection<String> ids);

    List<Place> findByCategory(String category);

    List<Place> findBySource(String source);

    Page<Place> findByRegionContainingAndNameContaining(String region, String name, Pageable pageable);

    Page<Place> findByCategoryContainingAndNameContaining(String category, String name, Pageable pageable);

    default Page<Place> findVisibleByRegionAndName(String region, String name, Pageable pageable) {
        return findVisibleByRegionAndName(region, name, PromptPlaceCatalog.apiNames(), pageable);
    }

    @Query(
            value = """
                    select distinct place
                    from Place place, PlaceImage image
                    where image.place = place
                      and lower(image.copyrightCode) = 'type1'
                      and place.category in ('nature', 'culture', 'active')
                      and place.name in :names
                      and place.region like concat('%', :region, '%')
                      and place.name like concat('%', :name, '%')
                    """,
            countQuery = """
                    select count(distinct place.id)
                    from Place place, PlaceImage image
                    where image.place = place
                      and lower(image.copyrightCode) = 'type1'
                      and place.category in ('nature', 'culture', 'active')
                      and place.name in :names
                      and place.region like concat('%', :region, '%')
                      and place.name like concat('%', :name, '%')
                    """)
    Page<Place> findVisibleByRegionAndName(
            @Param("region") String region,
            @Param("name") String name,
            @Param("names") Set<String> names,
            Pageable pageable);

    default Page<Place> findVisibleByCategoryAndName(String category, String name, Pageable pageable) {
        return findVisibleByCategoryAndName(category, name, PromptPlaceCatalog.apiNames(), pageable);
    }

    @Query(
            value = """
                    select distinct place
                    from Place place, PlaceImage image
                    where image.place = place
                      and lower(image.copyrightCode) = 'type1'
                      and place.category in ('nature', 'culture', 'active')
                      and place.category like concat('%', :category, '%')
                      and place.name in :names
                      and place.name like concat('%', :name, '%')
                    """,
            countQuery = """
                    select count(distinct place.id)
                    from Place place, PlaceImage image
                    where image.place = place
                      and lower(image.copyrightCode) = 'type1'
                      and place.category in ('nature', 'culture', 'active')
                      and place.category like concat('%', :category, '%')
                      and place.name in :names
                      and place.name like concat('%', :name, '%')
                    """)
    Page<Place> findVisibleByCategoryAndName(
            @Param("category") String category,
            @Param("name") String name,
            @Param("names") Set<String> names,
            Pageable pageable);

}
