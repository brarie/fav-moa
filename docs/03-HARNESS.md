# 하네스 엔지니어링 & 사용자 준비물

"하네스 엔지니어링" = Claude Code가 이 리포에서 **스스로 검증하며** 일할 수 있게 만드는 장치들.
원칙: **모든 Phase의 완료 기준은 사람이 눈으로 보지 않아도 명령 한 줄로 확인 가능해야 한다.**

## 1. 리포에 심을 장치 (Claude가 Phase 0에서 셋업)

### 1.1 단일 검증 명령

```bash
pnpm verify   # = web(lint + typecheck + vitest) + server(gradlew check: ktlint + JUnit)
# detekt는 안정판(1.23.x)이 Kotlin 2.3 미지원 → 2.0 안정판 출시 시 도입
```

- Claude는 어떤 변경이든 커밋 전 `pnpm verify`를 돌린다. 이 명령이 프로젝트의 "녹색 불".
- CI(GitHub Actions)도 같은 명령을 실행 — 로컬과 CI의 검증이 항상 동일.
- API 스펙 변경 시 `pnpm gen:api`(openapi → TS 타입 재생성) 후 web 타입체크가 계약 위반을 잡는다.

### 1.2 fixture 기반 파서 테스트 (이 프로젝트 하네스의 핵심)

- 네이버 블로그/디시/티스토리 등 실제 페이지 HTML을 `server/src/test/resources/fixtures/<site>/<case>.html`로 저장.
- 각 fixture 옆에 기대 결과 `expected.json` (title, 본문 첫 200자, canonicalUrl 등) — JUnit 파라미터라이즈드 테스트가 일괄 검증.
- 효과: **네트워크 없이** 파서를 검증 → Claude가 파서를 고칠 때마다 즉시 회귀 확인 가능. 사이트 구조가 바뀌면 fixture만 갱신.
- fixture 수집 헬퍼: `gradlew fixtureAdd --args="<url>"` → HTML 저장 + expected.json 골격 생성.

### 1.3 눈으로 확인용 CLI 러너 (Gradle task)

- `gradlew extract --args="<url>"` — 추출 파이프라인만 단독 실행, 결과 JSON 출력 (LLM 미호출)
- `gradlew analyze --args="<url>"` — 추출 + LLM까지 전체 파이프라인 실행 (API 키 필요)
- 효과: 웹 UI 없이도 백엔드 파이프라인을 단계별로 디버깅 가능.

### 1.4 시드 데이터

- `gradlew seed` — PENDING / PROCESSING / DONE / FAILED / 핀 고정 / 태그 다수 등 **모든 UI 상태**를 커버하는 가짜 북마크 생성.
- 효과: Claude가 프론트 작업 시 실제 스크래핑 없이 모든 카드 상태를 화면에서 재현.

### 1.5 CLAUDE.md (리포 루트)

포함 내용: 명령 목록(dev/verify/seed/extract/gen:api), 리포 지도(web/server), "파서 수정 시 fixture 테스트 필수", "API 변경 시 gen:api 필수" 같은 프로젝트 규칙, 디자인 토큰 위치, 02-DESIGN.md의 "하지 않을 것" 요약.

### 1.6 .claude/ 설정

- `settings.json` 권한 allowlist: `pnpm *`, `docker compose *`, `gradlew *` 등 — 매번 승인 프롬프트 없이 진행되도록.
- 스킬(선택): `/verify`(빌트인 verify가 프로젝트 스킬 부트스트랩), 앱 기동+스모크용 run 스킬.
- 포맷 훅(선택): 파일 편집 후 prettier 자동 실행.

### 1.7 Playwright 스모크 (Phase 4쯤 도입)

로그인 우회용 테스트 세션 → "옴니바에 URL 입력 → 카드 등장 → (시드 워커 목킹) 완료 상태 전환" 시나리오 1~2개만. E2E는 얇게 유지.

## 2. 사용자(당신)가 해야 할 일 체크리스트

### 지금 (Phase 0 시작 전)

역할 분담 합의: GUI 설치·계정·키 발급은 사용자가, CLI로 되는 것(compose 작성, 컨테이너 기동, 마이그레이션, 코드 전부)은 Claude가.

- [x] ~~JDK~~ — Corretto 24 설치돼 있음. 빌드 타겟 JDK 21은 Gradle toolchain(foojay resolver)이 자동 다운로드 (+ IntelliJ IDEA 권장 — 코드 리뷰용)
- [x] ~~Node.js 22 + pnpm~~ — 확인 완료 (v22.18 / pnpm 10)
- [x] ~~Docker Desktop~~ — 확인 완료, postgres+redis 컨테이너 기동됨
- [x] ~~git init·GitHub~~ — https://github.com/brarie/fav-moa (public) 연결 완료
- [x] ~~00-PLAN.md §5 결정 항목~~ — 2026-07-23 최종 확정 (Spring Boot 3+Kotlin / JPA+Flyway / 잡 테이블 큐 / JWT+Refresh / 컬러 B안 심록 · 폴더 중심+공유 / 개인 서버 docker compose)

### Phase 3 전 (LLM 연동 전까지만 있으면 됨)

- [ ] **Gemini API 키** 발급: https://aistudio.google.com → API Keys → `.env`에 넣을 키 확보 (무료 티어로 개발 충분)

### Phase 1 전

- [ ] **Google OAuth 클라이언트** 생성: https://console.cloud.google.com → API 및 서비스 → 사용자 인증 정보 → OAuth 클라이언트 ID(웹)
  - 승인된 리디렉션 URI: `http://localhost:8080/login/oauth2/code/google` (Spring Security 기본 컨벤션)
  - Client ID / Secret을 `.env`에

### Phase 2 전 — ★ 가장 중요한 기여

- [ ] **실사용 URL 코퍼스 20~30개**: 평소 저장하고 싶었던 실제 링크들 (네이버 블로그, 티스토리, 디시, 뉴스, 유튜브, 깃헙 등 골고루). `docs/corpus.md`에 붙여넣기만 하면 됨.
  - 이것이 fixture 테스트의 원천이자 LLM 프롬프트 튜닝의 기준. 내 실제 사용 패턴에 맞는 제품이 되는 핵심 재료.

### 개발 중 계속

- [ ] Phase가 끝날 때마다 실제로 써보고 피드백 (특히 태그 품질·요약 톤 — 프롬프트는 당신 취향으로 수렴시켜야 함)
- [ ] GitHub 리포 만들 거면 `gh auth login` 해두기 (Claude가 push/PR 가능해짐)

### 배포 시점 (Phase 5) — 개인 서버 docker compose로 확정

- [ ] 서버 접속 정보·도메인·리버스 프록시(Caddy/Nginx) 방침 공유 — compose 파일(web/api/worker/postgres/redis + docker network)은 Claude가 작성. 그때 다시 논의.

## 3. Claude Code 작업 규약 (개발 시작 후 CLAUDE.md로 이관)

1. 모든 변경은 `pnpm verify` 녹색 후 커밋. 파서 변경은 fixture 테스트 동반 필수.
2. 기능 단위 브랜치(`feat/<이슈번호>-<슬러그>`) → PR 본문에 DoD 체크리스트 → 사용자 리뷰 후 merge commit (squash 금지). 상세는 CLAUDE.md "Git 워크플로".
3. 프론트 변경은 시드 데이터 기준 스크린샷으로 확인 (02-DESIGN.md 토큰 준수).
4. 새 라이브러리 도입은 근거 한 줄과 함께 — 01-ARCHITECTURE.md의 결정을 뒤집을 땐 문서부터 수정.
