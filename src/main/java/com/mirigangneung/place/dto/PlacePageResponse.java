package com.mirigangneung.place.dto;
import org.springframework.data.domain.Page; import java.util.*;
public record PlacePageResponse(List<PlaceResponse> content,int page,int size,long totalElements,int totalPages){ public static PlacePageResponse from(Page<com.mirigangneung.place.domain.Place> p){return new PlacePageResponse(p.getContent().stream().map(PlaceResponse::from).toList(),p.getNumber(),p.getSize(),p.getTotalElements(),p.getTotalPages());}}
