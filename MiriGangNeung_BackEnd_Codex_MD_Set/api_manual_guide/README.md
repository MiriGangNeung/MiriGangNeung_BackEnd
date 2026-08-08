# api_manual_guide

이 폴더는 Codex가 한국관광공사 OpenAPI를 구현할 때 참고하는 자료다.

## 구조

- `original/`: 사용자가 제공한 원본 DOCX
- `markdown/`: 원본 DOCX를 문단/표 중심으로 변환한 Markdown

## 사용 규칙

1. 프로젝트 기능에 직접 필요한 API는 `docs/19_API_MANUAL_INDEX.md`를 먼저 본다.
2. 정확한 endpoint/parameter/response field를 구현할 때는 `original/`의 원본 문서를 최종 확인한다.
3. Markdown은 검색 편의를 위한 참고본이다.
4. 원본에 없는 내용을 추측해서 API 계약을 만들지 않는다.
