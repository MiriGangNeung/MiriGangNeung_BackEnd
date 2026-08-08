package com.mirigangneung.place.controller;
import com.mirigangneung.place.dto.*; import com.mirigangneung.place.service.PlaceService; import jakarta.validation.constraints.*; import org.springframework.validation.annotation.Validated; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/v1/places") @Validated public class PlaceController { private final PlaceService service; public PlaceController(PlaceService s){service=s;}
 @GetMapping public PlacePageResponse list(@RequestParam(required=false)String category,@RequestParam(defaultValue="")String keyword,@RequestParam(defaultValue="0")@Min(0)int page,@RequestParam(defaultValue="20")@Min(1)@Max(100)int size){return service.search(category,keyword,page,size);}
 @GetMapping("/{placeId}") public PlaceDetailResponse detail(@PathVariable String placeId){return service.detail(placeId);}
 @GetMapping("/{placeId}/nearby") public List<PlaceResponse> nearby(@PathVariable String placeId){return service.candidates(placeId,false);}
 @GetMapping("/{placeId}/related") public List<PlaceResponse> related(@PathVariable String placeId){return service.candidates(placeId,true);}
}
