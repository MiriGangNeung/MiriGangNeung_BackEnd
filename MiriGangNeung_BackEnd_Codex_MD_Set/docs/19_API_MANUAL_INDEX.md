# 19 Korean Tourism API Manual Index

## 원본

`api_manual_guide/original/`

사용자가 제공한 원본 DOCX를 보존한다.

## Markdown 변환본

`api_manual_guide/markdown/`

Codex가 빠르게 검색할 수 있도록 DOCX의 문단/표를 Markdown으로 변환했다.

### 주의

변환 과정에서 Word의 복잡한 레이아웃, 이미지, 도형, 일부 병합 셀의 시각적 의미가 손실될 수 있다.

**실제 endpoint, parameter, response field를 구현할 때는 원본 DOCX를 최종 확인한다.**

## 프로젝트 관련 우선순위

### 최우선

1. 국문 관광정보 서비스
2. 관광사진
3. 관광지별 연관 관광지
4. 빅데이터 지역별 방문자수
5. 관광지 집중률/방문자 추이 관련 API

### 확장

6. 무장애여행
7. 반려동물동반여행
8. 고캠핑
9. 두루누비
10. 오디/웰니스 등 프로젝트와 실제 기능이 맞는 특화 API

### 무관/참고

- 다국어 관광정보 매뉴얼
- 관광공모전 수상작
- 관광인 채용정보

다국어 매뉴얼은 현재 한국어 서비스의 핵심 구현에 직접 필요하지 않지만 원본 보존 차원에서 포함한다.

## API 사용 원칙

- 프론트에서 관광공사 API 직접 호출 금지
- Backend client가 API key 관리
- raw response를 프론트에 직접 전달하지 않음
- 데이터 source/updatedAt 기록
- 장애 시 cache/fallback 고려
