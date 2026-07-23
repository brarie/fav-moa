# fav-moa (즐겨모아) — 마스터 플랜

> URL을 던지면 AI가 알아서 요약·태깅해서 정리해주는, 나만의 즐겨찾기 서랍.
> 한국 웹(네이버 블로그, 디시, 티스토리 등)을 제대로 파싱하는 것이 차별점.

관련 문서:

- [01-ARCHITECTURE.md](./01-ARCHITECTURE.md) — 기술 스택 결정과 근거, 데이터 모델, API
- [02-DESIGN.md](./02-DESIGN.md) — 프론트엔드 디자인 언어
- [03-HARNESS.md](./03-HARNESS.md) — Claude Code 하네스 엔지니어링 + 사용자 준비물

> §2의 "explain.md"는 Gemini와 작성했던 최초 기획서(삭제됨)를 가리킨다. 내용은 본 문서들로 대체.

---

## 1. 한 줄 비전과 사용자 스토리

**비전**: "읽을만한 링크를 발견 → 붙여넣기 → 끝." 정리는 시스템이 한다.

핵심 사용자 스토리 (MVP):

1. 나는 로그인한 상태에서 URL 하나를 붙여넣으면, 즉시 카드가 생기고(분석 중 표시), 몇 초 뒤 제목·요약·태그·썸네일이 채워진 카드로 바뀐다.
2. 나는 태그를 클릭하거나 검색어를 입력해서 예전에 저장한 링크를 다시 찾을 수 있다.
3. 나는 AI가 단 제목/요약/태그가 마음에 안 들면 직접 고칠 수 있다 (커스터마이징).
4. 나는 네이버 블로그나 디시 글을 저장해도 본문이 제대로 요약되어 나온다.

명시적 후순위 (스키마에 자리만 마련, 구현 안 함):

- 외부 공유 (공개 링크/컬렉션) — `visibility` 필드만 미리 둠
- 크롬 익스텐션
- Reader View (본문 전체 읽기 뷰) — 본문 텍스트는 저장하므로 나중에 붙이기 쉬움
- 시맨틱(임베딩) 검색 — Phase 5 선택 과제

## 2. explain.md 대비 확정 사항 (2026-07-23 최종 — Spring Boot 전환)

| 항목 | explain.md | 확정 | 비고 (상세는 01 문서) |
| --- | --- | --- | --- |
| 백엔드 | NestJS | **Spring Boot 3 + Kotlin (JDK 21)** | 포트폴리오 목적 + 사용자 주력 스택이라 면접 방어력 최대. FE(Next.js)와 분리 유지 — 익스텐션이 같은 API 소비 |
| ORM | Prisma | **Spring Data JPA(Hibernate) + Flyway** | 사용자 숙련 영역. 마이그레이션은 Flyway SQL로 명시 관리 |
| 큐 | Redis + BullMQ | **Postgres 잡 테이블 + `FOR UPDATE SKIP LOCKED` 폴링 워커** | 인프라 추가 없이 유실 방지·재시도·백오프·동시성 제한 직접 설계 — 면접 토크 포인트. Redis는 블랙리스트·pub/sub 용도로만 유지 |
| 인증 | (미정) | **Spring Security OAuth2(Google) + 자체 JWT access+refresh 회전** | refresh 해시는 Postgres, 블랙리스트는 Redis. 익스텐션도 동일 토큰 |
| FE/BE 계약 | (없음) | **springdoc-openapi → openapi-typescript 코드젠** | 폴리글랏 실무 표준 패턴 |
| LLM | Gemini 1.5 Flash | **Spring AI + Gemini 2.5 Flash** | structured output. Spring+LLM 조합 자체가 포트폴리오 가점 |
| 실시간 | SSE | **SSE (SseEmitter + Redis pub/sub)** + 폴링 폴백 | |
| 배포 | (미정) | **개인 서버 docker compose + docker network** | web/api/worker/postgres/redis 5컨테이너. 시점은 Phase 5 |

## 3. 로드맵

각 Phase는 **완료 기준(DoD)이 기계적으로 검증 가능**하도록 정의한다 (하네스 원칙, 03 문서 참고).

### Phase 0 — 리포 & 하네스 셋업

- 리포 스캐폴드: `web/`(Next.js, pnpm) + `server/`(Spring Boot 3 + Kotlin, Gradle Kotlin DSL)
- docker-compose로 Postgres(pgvector 이미지) + Redis 기동, Flyway 초기 마이그레이션
- CLAUDE.md, `.claude/` 스킬·설정, 루트 `pnpm verify` (web lint/typecheck/test + `gradlew check`) 단일 명령
- springdoc-openapi → openapi-typescript 코드젠 파이프라인 (`pnpm gen:api`)
- git init + 첫 커밋
- **DoD**: `docker compose up -d && pnpm verify` 클린 통과. `pnpm dev`로 web(3000) + server(8080)가 함께 뜨고 `/actuator/health` 응답.

### Phase 1 — 로그인 + 북마크 CRUD (AI 없음)

- server: Spring Security OAuth2 Client(Google) 콜백 → JWT access+refresh 발급/회전, 인증 필터로 API 보호
- web: 로그인 화면, 토큰 처리, URL 입력 → `POST /bookmarks` (PENDING) → 카드 그리드
- 카드 편집(제목/메모)·삭제, 시드 스크립트(다양한 상태의 가짜 데이터)
- **DoD**: 로그인 → URL 저장 → 목록 표시 → 편집/삭제가 실제 브라우저에서 동작. refresh 회전·로그아웃(블랙리스트)이 동작. 시드 데이터로 pending/done/failed 카드가 모두 렌더링됨.

### Phase 2 — 추출 파이프라인 (한국 웹 특화)

- `server/analyze/extractor`: URL resolver 체인 → jsoup fetch → Readability4J/커스텀 본문 추출 + 메타(og:title/og:image/favicon)
- 특화 resolver: 네이버 블로그(iframe 추적), 디시(모바일 URL 변환), 티스토리, 유튜브(메타만)
- **저장된 HTML fixture 기반 유닛 테스트** (`src/test/resources/fixtures/`) — 네트워크 없이 파서 검증 (하네스 핵심)
- **DoD**: `gradlew test` 에서 사이트별 fixture 테스트 전부 통과. CLI 러너 `gradlew extract --args="<url>"` 로 실제 URL 추출 결과를 눈으로 확인 가능.

### Phase 3 — LLM 분석 + 비동기 큐 + 실시간 반영

- `AnalyzeJob` 테이블 + `FOR UPDATE SKIP LOCKED` 폴링 워커(`worker` 프로파일 프로세스): 저장 → 잡 인큐 → 추출→LLM→DB 갱신, 실패 시 백오프 재시도/failReason 기록
- Spring AI + Gemini structured output: `{summary, tags[], category}` — 기존 사용자 태그 목록을 프롬프트에 주입해 태그 난립 방지
- 실시간: 워커 → Redis pub/sub → API `SseEmitter` → 카드 갱신 (진행 중일 때만 폴링 폴백)
- **DoD**: URL 저장 후 새로고침 없이 카드가 [분석 중]→[완료]로 바뀐다. 워커를 죽였다 살려도 잡이 유실되지 않는다. 실패 URL은 failed 카드 + 재시도 버튼.

### Phase 4 — 검색 & 커스터마이징

- 검색: 제목/요약/메모/태그 대상, pg_trgm 인덱스 + ILIKE (한국어 부분일치에 실용적)
- 태그 필터(사이드바), 태그 rename/색상/병합, 카드 핀 고정
- AI 결과 수동 수정(요약/태그 편집) — 수정본이 검색 대상
- **DoD**: 시드 50개 기준 검색·태그 필터가 200ms 내 응답. 태그 병합 후 카드들이 올바르게 재연결됨.

### Phase 5 — 폴리시 & 선택 과제

- 디자인 마감(빈 상태, 로딩, 에러, 반응형), 다크 모드
- (선택) pgvector + Gemini 임베딩으로 시맨틱 검색 → 키워드와 하이브리드
- (선택) Reader View, 공유 기능 착수, 배포(Vercel + Neon/Supabase 또는 VPS)

## 4. 리스크와 대응

| 리스크 | 대응 |
| --- | --- |
| 스크래핑 차단 (Cloudflare, 봇 차단, robots) | 일반 UA + 타임아웃, 실패 시 og 메타만이라도 저장하는 degrade 경로. 차단 심한 사이트는 "메타 전용" 목록 관리 |
| 네이버/디시 DOM 구조 변경 | fixture 테스트가 회귀를 즉시 잡음. resolver는 사이트당 파일 하나로 격리 |
| LLM 무료 쿼터/비용 | Flash 계열 + 본문 앞 N자 절단(토큰 상한). 재시도는 지수 백오프, 쿼터 초과는 잡을 지연 재큐 |
| 한국어 검색 품질 (형태소 분석 없음) | pg_trgm으로 MVP 충분. 부족하면 Phase 5 임베딩 검색이 근본 해결 |
| 본문 없는 페이지 (SPA, 유튜브, 페이월) | resolver가 "메타 전용" 모드로 판정 → 메타데이터 기반 요약 또는 요약 생략 |

## 5. 사용자 결정 사항 (2026-07-23 최종 확정)

1. ~~백엔드~~ → **Spring Boot 3 + Kotlin** (포트폴리오 목적, NestJS안 폐기).
2. ~~ORM~~ → **Spring Data JPA + Flyway** (Prisma안 폐기).
3. ~~큐~~ → **Postgres 잡 테이블 + SKIP LOCKED** (BullMQ안 폐기, Redis는 블랙리스트·pub/sub 전용).
4. ~~로그인~~ → **Google OAuth + 자체 JWT(access/refresh 회전)**.
5. ~~액센트 컬러~~ → **A안 (먹 + 주홍)**.
6. ~~배포~~ → **개인 서버, docker compose + docker network** (시점은 Phase 5).
7. **실사용 URL 코퍼스** (유일하게 남은 준비물): 평소 저장하는 실제 URL 20~30개 → `docs/corpus.md` (03 문서 참고 — fixture와 프롬프트 튜닝에 필수).
