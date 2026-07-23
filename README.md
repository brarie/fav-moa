# fav-moa · 즐겨모아

> URL을 붙여넣으면 끝. 스크래핑 → AI 요약·태깅 → 검색까지, 정리는 시스템이 한다.

한국 웹(네이버 블로그 iframe, 디시 모바일 등)을 제대로 파싱하는 것이 차별점인 AI 북마크 매니저.

## Stack

| | |
| --- | --- |
| Backend | Spring Boot 4.1 · Kotlin · JDK 21 · Spring Data JPA · Flyway |
| Async | Postgres 잡 테이블 + `FOR UPDATE SKIP LOCKED` 폴링 워커 |
| AI | Spring AI · Gemini 2.5 Flash (structured output) |
| Frontend | Next.js 16 · TypeScript · Tailwind CSS v4 |
| Infra | PostgreSQL 17 (pgvector) · Redis 7 · docker compose |
| Auth | Google OAuth2 + 자체 JWT (access/refresh 회전) |

## Docs

설계 문서는 [`docs/`](./docs) 참고 — [계획·로드맵](./docs/00-PLAN.md) · [아키텍처](./docs/01-ARCHITECTURE.md) · [디자인](./docs/02-DESIGN.md) · [엔지니어링 하네스](./docs/03-HARNESS.md)

## Dev

```bash
docker compose up -d   # postgres + redis
pnpm install
pnpm dev               # web :3000 / server :8080
pnpm verify            # lint + typecheck + test
```
