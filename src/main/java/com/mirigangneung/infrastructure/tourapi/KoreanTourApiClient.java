package com.mirigangneung.infrastructure.tourapi;

import com.fasterxml.jackson.databind.*; import com.fasterxml.jackson.dataformat.xml.XmlMapper; import com.mirigangneung.common.error.ApiException; import org.springframework.http.*; import org.springframework.stereotype.Component; import org.springframework.web.client.RestClient; import org.springframework.web.util.UriComponentsBuilder; import java.net.URI; import java.util.*;

@Component
public class KoreanTourApiClient implements TourApiClient {
 private final TourApiProperties props; private final RestClient client; private final ObjectMapper xml=new XmlMapper();
 public KoreanTourApiClient(TourApiProperties p){props=p;client=RestClient.builder().baseUrl(p.baseUrl()).build();}
 public List<TourPlace> search(String keyword,String category,int page,int size){ if(props.key()==null||props.key().isBlank()) return List.of(); String endpoint=(keyword!=null&&!keyword.isBlank())?"searchKeyword2":"areaBasedList2"; Map<String,Object> params=new HashMap<>(); params.put("contentTypeId","12");params.put("areaCode","32");params.put("pageNo",page+1);params.put("numOfRows",size);params.put("arrange","A");if(keyword!=null&&!keyword.isBlank())params.put("keyword",keyword);return call(endpoint,params); }
 public Optional<TourPlace> find(String id){ if(props.key()==null||props.key().isBlank()) return Optional.empty(); return call("detailCommon2",Map.of("contentId",id,"defaultYN","Y","firstImageYN","Y","overviewYN","Y")).stream().findFirst(); }
 public List<TourPlace> related(String id){ if(props.key()==null||props.key().isBlank()) return List.of(); return call("detailInfo2",Map.of("contentId",id,"contentTypeId","12")); }
 private List<TourPlace> call(String path,Map<String,Object> params){ try { var b=UriComponentsBuilder.fromPath("/"+path).queryParam("serviceKey",props.key()).queryParam("MobileOS","ETC").queryParam("MobileApp","MiriGangNeung").queryParam("_type","json"); params.forEach(b::queryParam); String body=client.get().uri(URI.create(b.build().encode().toUriString())).retrieve().body(String.class); return parse(body); } catch(Exception e){ throw new ApiException("TOUR_API_ERROR",HttpStatus.BAD_GATEWAY,"관광공사 API를 사용할 수 없습니다."); } }
 private List<TourPlace> parse(String body){ try { JsonNode root=body.trim().startsWith("<")?xml.readTree(body):new ObjectMapper().readTree(body); JsonNode items=root.at("/response/body/items/item"); if(items.isMissingNode()) items=root.at("/response/body/items"); List<TourPlace> out=new ArrayList<>(); if(items.isArray()) items.forEach(n->out.add(map(n))); else if(items.isObject()) out.add(map(items)); return out; } catch(Exception e){throw new ApiException("TOUR_API_ERROR",HttpStatus.BAD_GATEWAY,"관광공사 응답을 해석할 수 없습니다.");} }
 private TourPlace map(JsonNode n){return new TourPlace(text(n,"contentid"),text(n,"title"),text(n,"addr1"),text(n,"contenttypeid"),text(n,"overview"),number(n,"mapy"),number(n,"mapx"),text(n,"firstimage"),List.of());}
 private String text(JsonNode n,String k){return n.path(k).asText(null);} private Double number(JsonNode n,String k){try{return n.path(k).isMissingNode()?null:n.path(k).asDouble();}catch(Exception e){return null;}}
}
