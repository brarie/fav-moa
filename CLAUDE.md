# fav-moa (즐겨모아)

URL을 넣으면 AI가 요약·태깅하는 한국 웹 특화 북마크 매니저. **계획과 규칙의 원천은 `docs/` — 작업 전 반드시 확인.**

- `docs/00-PLAN.md` — Phase 로드맵과 각 Phase의 완료 기준(DoD)
- `docs/01-ARCHITECTURE.md` — 스택 결정·데이터 모델·API. 결정을 뒤집을 땐 문서부터 수정
- `docs/02-DESIGN.md` — 디자인 토큰과 "하지 않을 것" 목록 (보라 그라데이션·glassmorphism·이모지 남발 금지)
- `docs/03-HARNESS.md` — 검증 전략과 사용자 준비물

## 구조

- `web/` — Next.js 16 (App Router, TS, Tailwind v4, pnpm). **Next 16은 breaking change 많음 — `web/AGENTS.md` 지침대로 `web/node_modules/next/dist/docs/` 먼저 읽을 것**
- `server/` — Spring Boot 4.1 + Kotlin (JDK 21 toolchain, Gradle Kotlin DSL). 패키지 루트 `moa`
- `docker-compose.yml` — dev용 Postgres(pgvector) + Redis

## 명령 (Windows, 리포 루트 기준)

| 명령 | 역할 |
| --- | --- |
| `docker compose up -d` | dev DB/Redis 기동 (서버 실행·테스트 전 필수) |
| `pnpm dev` | web(3000) + server(8080) 동시 기동 |
| `pnpm verify` | **커밋 전 필수** — web lint+typecheck + server `gradlew check` |
| `cd server; ./gradlew.bat test` | 서버 테스트만 |

## 규칙

1. 커밋 전 `pnpm verify` 녹색. 실패한 채 커밋 금지.
2. 스키마 변경은 Flyway 마이그레이션(`server/src/main/resources/db/migration/`)으로만. `ddl-auto`는 validate 고정.
3. 파서(extractor) 수정 시 `server/src/test/resources/fixtures/` 기반 테스트 동반 필수.
4. API 계약 변경 시 OpenAPI → web 타입 재생성 (`pnpm gen:api`, Phase 1에서 구축).
5. Spring Boot **4.x** 기준 — 3.x 시절 지식(starter-web 등)과 다른 부분은 공식 문서 확인.

## Git 워크플로 (2026-07-23 합의)

- **main은 항상 `pnpm verify` 그린** (배포 가능 상태 유지).
- 브랜치: `feat/<이슈번호>-<슬러그>` (이슈 없으면 번호 생략), `fix/…`, `refactor/…`. Phase는 GitHub 이슈/마일스톤으로 추적.
- **PR 단위 = 기능** (Phase당 2~3개 규모). PR 본문에 해당 DoD 항목 체크리스트 포함. 사용자가 리뷰 후 머지.
- **머지는 merge commit, squash 금지** — stacked 브랜치에서 커밋 해시 유실로 인한 중복 문제 방지 (사용자 경험칙).
- 커밋 위생: 각 커밋은 빌드 가능 + conventional 메시지(`feat:`/`fix:`/`docs:`/`chore:` + 한국어 본문). wip 커밋 금지.
- docs·설정 등 소소한 변경은 main 직커밋 허용.
- 커밋 트레일러는 `Co-Authored-By: Claude <noreply@anthropic.com>` — 모델명은 넣지 않음 (사용자 요청).
