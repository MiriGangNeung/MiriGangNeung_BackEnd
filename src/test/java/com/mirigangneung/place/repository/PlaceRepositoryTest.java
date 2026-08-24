package com.mirigangneung.place.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mirigangneung.place.domain.Place;
import com.mirigangneung.place.domain.PlaceImage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:place-repository-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "tour.api.sync-on-startup=false"
})
@Transactional
class PlaceRepositoryTest {

    @Autowired private PlaceRepository placeRepository;
    @Autowired private PlaceImageRepository placeImageRepository;

    @Test
    void listsOnlyPlacesThatHaveAtLeastOneType1Image() {
        Place visible = savePlace("visible", "경포해변", "nature");
        Place forbidden = savePlace("forbidden", "Type3 장소", "nature");
        Place food = savePlace("food", "배경과 무관한 음식점", "food");
        savePlace("no-image", "사진 없는 장소", "nature");
        placeImageRepository.save(new PlaceImage(
                visible, "https://img.test/visible.jpg", "대표", "KTO", 0, "Type1"));
        placeImageRepository.save(new PlaceImage(
                forbidden, "https://img.test/forbidden.jpg", "대표", "KTO", 0, "Type3"));
        placeImageRepository.save(new PlaceImage(
                food, "https://img.test/food.jpg", "대표", "KTO", 0, "Type1"));

        var result = placeRepository.findVisibleByRegionAndName(
                "강릉", "", PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(Place::getTourContentId).containsExactly("visible");
    }

    @Test
    void appliesCategoryAndKeywordToPlacesWithType1Images() {
        Place beach = savePlace("beach", "안목해변", "nature");
        Place museum = savePlace("museum", "안목미술관", "culture");
        placeImageRepository.saveAll(List.of(
                new PlaceImage(beach, "https://img.test/beach.jpg", "대표", "KTO", 0, "Type1"),
                new PlaceImage(museum, "https://img.test/museum.jpg", "대표", "KTO", 0, "Type1")));

        var result = placeRepository.findVisibleByCategoryAndName(
                "culture", "안목", PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(Place::getTourContentId).containsExactly("museum");
    }

    private Place savePlace(String contentId, String name, String category) {
        return placeRepository.save(new Place(
                contentId,
                name,
                "강원특별자치도 강릉시",
                category,
                "설명",
                37.75,
                128.90,
                null,
                "KTO"));
    }
}
