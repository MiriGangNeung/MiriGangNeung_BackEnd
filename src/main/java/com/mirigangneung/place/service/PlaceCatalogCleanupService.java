package com.mirigangneung.place.service;

import com.mirigangneung.course.repository.CourseStopRepository;
import com.mirigangneung.place.domain.Place;
import com.mirigangneung.place.repository.PlaceImageRepository;
import com.mirigangneung.place.repository.PlaceRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlaceCatalogCleanupService {
    private final PlaceRepository placeRepository;
    private final PlaceImageRepository placeImageRepository;
    private final CourseStopRepository courseStopRepository;

    public PlaceCatalogCleanupService(
            PlaceRepository placeRepository,
            PlaceImageRepository placeImageRepository,
            CourseStopRepository courseStopRepository) {
        this.placeRepository = placeRepository;
        this.placeImageRepository = placeImageRepository;
        this.courseStopRepository = courseStopRepository;
    }

    @Transactional
    public int deleteFoodPlaces() {
        List<Place> foodPlaces = placeRepository.findByCategory("food");
        if (foodPlaces.isEmpty()) {
            return 0;
        }
        courseStopRepository.deleteByPlaceIn(foodPlaces);
        placeImageRepository.deleteByPlaceIn(foodPlaces);
        placeRepository.deleteAll(foodPlaces);
        return foodPlaces.size();
    }
}
