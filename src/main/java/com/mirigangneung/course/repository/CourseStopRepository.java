package com.mirigangneung.course.repository;

import com.mirigangneung.course.domain.Course;
import com.mirigangneung.course.domain.CourseStop;
import com.mirigangneung.place.domain.Place;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseStopRepository extends JpaRepository<CourseStop, UUID> {
    List<CourseStop> findByCourseOrderBySequenceAsc(Course course);

    void deleteByPlaceIn(List<Place> places);
}
