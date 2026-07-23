# 아키텍처 (확정: 2026-07-23, Spring Boot 전환)

프론트(Next.js)와 백엔드(**Spring Boot 4 + Kotlin**)를 분리한다.

- 분리 이유: 향후 크롬 익스텐션이 같은 API를 소비.
- Spring 선택 이유: 포트폴리오 목적 + 사용자가 깊게 방어/확장 가능한 스택. 한국 백엔드 채용 시장의 주류.
- Kotlin 선택: 사용자 결정 (Spring 공식 1급 지원, 간결한 문법).

## 1. 스택 결정과 근거

| 구분 | 선택 | 근거 |
| --- | --- | --- |
| 백엔드 | **Spring Boot 4.1 + Kotlin (JDK 21 toolchain)**, Gradle Kotlin DSL | 사용자 주력 스택. 3.5는 Initializr에서 내려가서(OSS 지원 종료) 현행 4.1 채택 — 스타터 이름 등 3.x 지식과 다른 부분 주의. Virtual Threads로 스크래핑 I/O 동시성 처리 (포트폴리오 포인트) |
| 프론트 | **Next.js 16 (App Router)** — 순수 API 클라이언트 | 대시보드 UI 전용. 얇게 유지. Next 16은 breaking change 많음 — `web/AGENTS.md` 참고 |
| DB / ORM | **PostgreSQL 17 (pgvector 이미지) + Spring Data JPA(Hibernate) + Flyway** | JPA는 사용자 숙련 영역. 마이그레이션은 Flyway로 SQL 명시 관리(pg_trgm·pgvector 인덱스 등 raw SQL 필요). 가변 데이터(LLM raw)는 `jsonb` 컬럼 매핑 |
| 큐 | **Postgres 잡 테이블 + `FOR UPDATE SKIP LOCKED` 폴링 워커** | 인프라 추가 없이 유실 방지·재시도·백오프·동시성 제한을 직접 설계 — 면접 방어력 최상. MQ 필요 규모 아님. 워커는 같은 앱을 `worker` 프로파일로 별도 프로세스 기동 |
| 인증 | **Spring Security OAuth2 Client(Google) + 자체 JWT(access 15분 + refresh 14일 회전)** | 스프링 정석 구현. refresh는 해시로 Postgres 저장 + 회전 체인 재사용 감지. 강제 만료는 Redis 블랙리스트 |
| Redis | **Redis 7** — access token 블랙리스트 + pub/sub(워커→API SSE 중계) + 캐시 | 큐 역할은 아님(큐는 Postgres). 가볍게 유지 |
| LLM | **Spring AI + Gemini 2.5 Flash** (structured output) | "Spring + LLM 파이프라인" 조합 자체가 가점. 임베딩은 Phase 5 |
| 스크래핑 | **jsoup** + Readability4J(본문 추출) + 사이트별 커스텀 resolver | 네이버 iframe/디시 모바일 등 특화 로직은 어차피 수제작 — jsoup으로 충분 |
| 실시간 | **SSE (`SseEmitter`)** + Redis pub/sub, 진행 중일 때만 폴링 폴백 | |
| FE/BE 계약 | **springdoc-openapi → `openapi-typescript` 코드젠** | zod 공유 대신 폴리글랏 실무 표준. 익스텐션도 같은 스펙 소비 |
| 스타일 | Tailwind CSS v4 (+ shadcn/ui 로직만 차용) | 02-DESIGN.md 자체 토큰 |
| 배포 | **docker compose + docker network** (개인 서버) | web / api / worker / postgres / redis. api·worker는 같은 이미지, 프로파일로 분기 |

## 2. 리포 구조

```
fav-moa/
├─ web/                       # Next.js (pnpm)
│  └─ src/lib/api/schema.d.ts #   openapi-typescript 생성물 (커밋함)
├─ server/                    # Spring Boot (Gradle Kotlin DSL)
│  └─ src/main/kotlin/moa/
│     ├─ auth/                #   OAuth2 콜백, JWT 발급/회전, SecurityConfig
│     ├─ bookmark/            #   controller / service / repository / entity
│     ├─ tag/
│     ├─ analyze/             #   잡 폴링 워커, extractor(사이트별 resolver), LlmClient
│     ├─ jobqueue/            #   Job 엔티티, SKIP LOCKED 폴러, 재시도 정책
│     └─ common/              #   예외 처리, SSE 허브, Redis 설정
│  └─ src/test/resources/fixtures/   # 사이트별 실제 HTML — 네트워크 없는 파서 테스트
├─ docker-compose.yml         # dev: postgres+redis / prod: 전체 5컨테이너
├─ docs/
└─ CLAUDE.md
```

검증 명령: 루트에서 `pnpm verify` = `web` lint+typecheck+test + `server` `./gradlew check`(ktlint, detekt, JUnit). CI도 동일 명령.

## 3. 데이터 흐름

```
[브라우저] 옴니바에 URL 붙여넣기
   → POST /bookmarks (JWT)      api: Bookmark(PENDING) + AnalyzeJob(QUEUED) 저장 — 같은 트랜잭션, 202 즉시 응답
[worker (profile=worker)]
   → @Scheduled 폴러: SELECT ... FOR UPDATE SKIP LOCKED LIMIT n   (n = LLM rate limit 고려한 동시성)
   → extractor: resolver 선택 → jsoup fetch → 본문/메타 추출
   → Spring AI: structured output → {summary, tags, category}
   → 트랜잭션: Bookmark 갱신 + Tag upsert → DONE (실패: attempts+1, nextRunAt=백오프, 초과 시 FAILED)
   → Redis publish("bookmark:{userId}", event)
[api]
   → SSE 스트림(SseEmitter)이 Redis 구독 → 클라이언트 push → 카드 [분석 중]→[완료]
```

## 4. 데이터 모델 (JPA 엔티티 초안 — DDL은 Flyway로)

```
User          id(uuid), email(uq), name, avatarUrl, googleId(uq), createdAt
RefreshToken  id, userId(fk), tokenHash(uq), family, expiresAt, revokedAt
              — 회전 체인(family)으로 재사용 감지 시 세션 전체 무효화

Bookmark      id, userId(fk), url, canonicalUrl, status(PENDING|PROCESSING|DONE|FAILED),
              failReason, title, siteName, faviconUrl, thumbnailUrl,
              summary, note(사용자 메모), category, contentText, llmRaw(jsonb),
              pinned(bool), visibility(PRIVATE|PUBLIC), createdAt, updatedAt
              uq(userId, canonicalUrl)  — 중복 저장 시 기존 카드 반환
              idx(userId, status), idx(userId, createdAt)

Tag           id, userId(fk), name, color   — uq(userId, name)
BookmarkTag   (bookmarkId, tagId) 복합 PK

AnalyzeJob    id, bookmarkId(fk), status(QUEUED|RUNNING|DONE|FAILED),
              attempts, maxAttempts(3), nextRunAt, lastError, createdAt
              idx(status, nextRunAt)  — 폴러가 이 인덱스로 집어감
```

검색 인덱스(Flyway SQL): `pg_trgm` 확장 + `title/summary/note` GIN trigram. Phase 5에 `embedding vector(768)` + HNSW.

## 5. API 표면 (springdoc-openapi가 스펙 생성 → FE 타입 코드젠)

| 메서드/경로 | 역할 |
| --- | --- |
| `GET /auth/google` → `GET /auth/google/callback` | OAuth 시작/콜백 → access+refresh 발급 (refresh는 httpOnly 쿠키) |
| `POST /auth/refresh` / `POST /auth/logout` | 토큰 회전 / 무효화(+Redis 블랙리스트) |
| `POST /bookmarks` | `{url}` → 생성+잡 인큐, 202. 중복 canonicalUrl이면 기존 카드 반환 |
| `GET /bookmarks?q=&tag=&status=&cursor=` | 목록 (커서 페이지네이션, 검색·필터 겸용) |
| `PATCH /bookmarks/{id}` | title/summary/note/tags/pinned 수정 |
| `DELETE /bookmarks/{id}` | 삭제 |
| `POST /bookmarks/{id}/retry` | FAILED 재분석 (contentText 있으면 LLM만 재실행) |
| `GET /bookmarks/events` (SSE) | 내 북마크 상태 변경 스트림 (JWT) |
| `GET /tags` / `PATCH /tags/{id}` / `POST /tags/merge` | 태그 목록·수정(rename/색)·병합 |

## 6. LLM 설계 메모

- 입력: 정제 본문 앞 ~8,000자(초과 절단) + 메타(title, siteName).
- 출력 스키마: `{ summary: 한국어 2~3문장, tags: 2~5개 한국어 명사형, category }` — Spring AI structured output converter로 강제.
- **태그 난립 방지**: 해당 유저의 기존 태그 상위 ~50개를 프롬프트에 주입, "가능하면 기존 태그 재사용" 지시.
- 시스템 프롬프트에 한국 커뮤니티 은어/초성 해석 지시.
- 응답 원본은 `llmRaw` 저장 — 프롬프트 개선 후 일괄 재처리 가능.
- rate limit: 잡 폴러의 배치 크기 + 폴링 주기로 분당 호출 수 제어 (무료 티어 안전선).

## 7. 확장 포인트 (자리만 두는 것)

- **공유**: `visibility` + 추후 `Share(slug, ...)` 테이블.
- **크롬 익스텐션**: `POST /bookmarks` + 동일 JWT — OpenAPI 스펙에서 클라이언트 생성.
- **컬렉션/폴더**: 태그로 시작, 수요 생기면 추가.
- **큐 교체**: `JobQueue` 인터페이스 뒤에 SKIP LOCKED 구현을 숨김 — RabbitMQ 필요해지면 구현체만 교체.
- **워커 스케일아웃**: worker 프로파일 컨테이너 수 증가 (SKIP LOCKED가 자연 분배).
