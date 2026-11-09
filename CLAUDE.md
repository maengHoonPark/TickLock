# TickLock — 코드 작성 규칙

설계는 `docs/PLAN.md`를 따른다. 이 프로젝트는 포트폴리오 + 학습용이므로 **단순하고 읽기 쉬운 구조**를 우선한다.

## 공통
- 유지보수하기 좋은 간결한 코드. 한 클래스/함수는 한 가지 일만.
- 주석은 "무엇"이 아니라 **"왜 이렇게 하는지(원리)"** 를 한국어로 쓴다. 학습용이라 동시성·멱등성 같은 핵심 지점은 특히 자세히.
- 폴더 깊이 3단계 이내, 기능별 패키지/폴더로 묶는다.

## Frontend (TypeScript / Next.js App Router)
- `any` 절대 금지. 모르는 값은 `unknown` + 타입 가드/zod 검증.
- `enum` 대신 `as const` 객체 + 유니온 타입. 상태/메시지는 판별 유니온(discriminated union)으로 모델링하고 `never`로 exhaustive check.
- 외부 입력(API/WebSocket 응답)은 런타임 검증 후 타입으로 좁힌다.
- 최신 App Router 패턴: 기본은 Server Component, 상호작용이 필요한 곳만 `"use client"`.
- `tsconfig` strict + `noUncheckedIndexedAccess`.

## Backend (Java 17 / Spring Boot 3)
- 위 정신을 Java로 옮긴다: `null` 대신 `Optional`/명시적 예외, DTO는 `record`, 상태값은 `enum`+sealed 성향으로 닫힌 집합으로.
- 금액/가격은 `long`(원 단위). `double` 금지.
- 락은 트랜잭션 **바깥**에서 잡는다 (락 → 커밋 → 락 해제).
- 정합성의 최종 보증은 DB 제약(UNIQUE, CHECK, FK).
