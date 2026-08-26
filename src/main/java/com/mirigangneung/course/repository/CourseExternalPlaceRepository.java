package com.mirigangneung.course.repository;

import com.mirigangneung.course.domain.CourseExternalPlace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CourseExternalPlaceRepository extends JpaRepository<CourseExternalPlace, UUID> {
}
