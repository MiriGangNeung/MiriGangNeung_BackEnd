package com.mirigangneung.infrastructure.tourapi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TourCategoryMapperTest {
    @Test
    void mapsFrontendCategoriesToTourApiContentTypes() {
        assertThat(TourCategoryMapper.toContentTypeId("nature")).isEqualTo("12");
        assertThat(TourCategoryMapper.toContentTypeId("beach")).isEqualTo("12");
        assertThat(TourCategoryMapper.toContentTypeId("culture")).isEqualTo("14");
        assertThat(TourCategoryMapper.toContentTypeId("food")).isEqualTo("39");
        assertThat(TourCategoryMapper.toContentTypeId("active")).isEqualTo("28");
        assertThat(TourCategoryMapper.toContentTypeId("shopping")).isEqualTo("38");
        assertThat(TourCategoryMapper.toContentTypeId("lodging")).isEqualTo("32");
        assertThat(TourCategoryMapper.toContentTypeId("course")).isEqualTo("25");
        assertThat(TourCategoryMapper.toContentTypeId("event")).isEqualTo("15");
    }

    @Test
    void defaultsUnknownCategoryToTouristSpots() {
        assertThat(TourCategoryMapper.toContentTypeId(null)).isEqualTo("12");
        assertThat(TourCategoryMapper.toContentTypeId("unknown")).isEqualTo("12");
        assertThat(TourCategoryMapper.toContentTypeId("관광지")).isEqualTo("12");
    }

    @Test
    void mapsTourApiContentTypesToStableInternalCategories() {
        assertThat(TourCategoryMapper.toInternalCategory("12")).isEqualTo("nature");
        assertThat(TourCategoryMapper.toInternalCategory("14")).isEqualTo("culture");
        assertThat(TourCategoryMapper.toInternalCategory("15")).isEqualTo("event");
        assertThat(TourCategoryMapper.toInternalCategory("25")).isEqualTo("course");
        assertThat(TourCategoryMapper.toInternalCategory("28")).isEqualTo("active");
        assertThat(TourCategoryMapper.toInternalCategory("32")).isEqualTo("lodging");
        assertThat(TourCategoryMapper.toInternalCategory("38")).isEqualTo("shopping");
        assertThat(TourCategoryMapper.toInternalCategory("39")).isEqualTo("food");
    }
}
