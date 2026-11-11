# 백엔드 코드 읽는 법 (Spring 초심자용)

> "컨트롤러, 서비스만 봐왔는데 이 코드는 뭔지 모르겠다"는 사람을 위한 안내서.
> 결론부터: **익숙한 3층 구조는 그대로 있다.** 낯설어 보이는 건 "HTTP 요청이 아니라 시세/타이머로 시작하는 코드"가 많아서다.

---

## 1. 익숙한 부분부터 찾기

주문 등록은 여러분이 아는 구조 그대로다. `backend/src/main/java/com/ticklock/order/`

```
브라우저 ──POST /api/orders──▶ OrderController   (요청 받기, 값 검증)
                                    │
                                    ▼
                               OrderService      (비즈니스 규칙: 중복 요청 처리, 종목 확인)
                                    │
                                    ▼
                               OrderRepository   (DB 저장/조회)  ──▶ PostgreSQL
```

| 역할       | 파일                         | 한 줄 설명                                           |
| ---------- | ---------------------------- | ---------------------------------------------------- |
| Controller | `order/OrderController.java` | 주소(`/api/orders`)와 메서드를 연결, 입력값 검증     |
| Service    | `order/OrderService.java`    | 주문 등록·취소·조회의 "규칙"                         |
| Repository | `order/OrderRepository.java` | DB 접근. 인터페이스만 쓰면 Spring이 구현을 만들어 줌 |
| Entity     | `order/TriggerOrder.java`    | DB 테이블 한 행을 표현하는 클래스                    |

**먼저 이 4개 파일만 읽어 보세요.** 여기까지가 평소 보던 Spring이다.

---

## 2. 낯선 부분이 왜 낯선가

주문 등록은 "사용자가 요청을 보내면 응답한다"로 시작한다.
그런데 이 프로젝트에는 **요청 없이 스스로 도는 일**이 있다.

| 하는 일                          | 시작점                | 위치                     |
| -------------------------------- | --------------------- | ------------------------ |
| 가짜 시세를 초당 1,000건 만든다  | 타이머 (`@Scheduled`) | `tick/TickGenerator`     |
| 시세를 받아 처리한다             | Redis 메시지 도착     | `tick/TickSubscriber`    |
| 조건이 맞은 주문을 찾아 체결한다 | 시세가 도착할 때마다  | `execution/OrderMatcher` |
| 브라우저에 시세를 밀어준다       | 타이머 (100ms)        | `tick/TickBroadcaster`   |
| 시세를 DB에 모아서 저장한다      | 타이머 (1초)          | `tick/BatchTickStore`    |

그래서 **Controller가 없다.** 시작점이 HTTP 요청이 아니라 타이머/메시지이기 때문이다.

### 시세가 들어와서 체결되기까지 (이게 이 프로젝트의 핵심 흐름)

```
TickGenerator ──(Redis 채널 "ticks"에 발행)──▶ Redis
                                                 │
                                                 ▼
                                           TickSubscriber   ← 메시지가 오면 자동 호출됨
                                                 │
        ┌──────────────┬──────────────┬─────────┴────────┐
        ▼              ▼              ▼                  ▼
   PriceCache    TickBroadcaster   OrderMatcher       TickStore
  (최신가 메모리)  (브라우저로 전송)  (조건 맞는 주문?)   (DB에 저장)
                                        │
                              조건이 맞으면
                                        ▼
                                  OrderExecutor  ──▶ 잔액 차감, 보유 증가, 체결 기록
                              (Safe: Redisson 락 사용)
```

---

## 3. 패키지 지도

```
com/ticklock/
├─ TickLockApplication     프로그램 시작점 (main). @EnableScheduling = 타이머 기능 켜기
├─ config/                 설정 (Redis 구독, CORS)
├─ common/                 공통 오류 처리 ({"message": "..."} 모양으로 통일)
├─ tick/                   시세: 생성 → 수신 → 캐시 → 전송 → 저장
├─ order/                  주문: 등록/취소/조회 + 대기 주문 메모리 인덱스
├─ execution/              체결: 조건 맞는 주문을 실제로 처리 (동시성의 핵심)
└─ wallet/                 지갑·보유 종목·평균 매입가
```

---

## 4. 낯선 키워드 사전

### Spring이 대신 해주는 것

| 보이는 것                                     | 뜻                                                                    |
| --------------------------------------------- | --------------------------------------------------------------------- |
| `@Component` / `@Service` / `@RestController` | "이 클래스 객체는 Spring이 만들어서 관리해라." 우리가 `new`를 안 쓴다 |
| 생성자에 다른 클래스를 받는 코드              | **의존성 주입(DI).** 필요한 객체를 Spring이 찾아서 넣어 준다          |
| `@Scheduled(fixedRate = 100)`                 | "100ms마다 이 메서드를 실행해라." 타이머                              |
| `@Transactional`                              | "이 메서드 안의 DB 작업을 전부 성공하거나 전부 취소해라"              |
| `@Value("${...}")`                            | `application.yml`의 설정값을 가져온다                                 |
| `@ConditionalOnProperty`                      | "설정이 이 값일 때만 이 클래스를 사용해라." → 구현 교체에 쓴다        |

### 자바 문법 (처음 보면 낯선 것)

| 보이는 것                                      | 뜻                                                                                       |
| ---------------------------------------------- | ---------------------------------------------------------------------------------------- |
| `record Tick(String symbol, long price, ...)`  | 값을 담는 불변 클래스를 한 줄로. getter/equals 자동 생성                                 |
| `interface OrderExecutor` + 구현 2개           | 같은 일을 하는 두 가지 방법(NaiveOrderExecutor / SafeOrderExecutor)을 설정으로 갈아 끼움 |
| `sealed interface ExecutionResult`             | "결과는 Executed / Failed / Skipped 셋뿐"이라고 컴파일러에 알림                          |
| `result instanceof ExecutionResult.Executed e` | 타입을 확인하고 바로 그 타입 변수로 꺼내 쓰기                                            |
| `ConcurrentHashMap`, `CopyOnWriteArrayList`    | 여러 스레드가 동시에 써도 안 깨지는 컬렉션                                               |

### 이 프로젝트 고유 개념

| 개념          | 한 줄 설명                                               | 코드                                  |
| ------------- | -------------------------------------------------------- | ------------------------------------- |
| Redis Pub/Sub | 한쪽이 채널에 메시지를 던지면, 구독 중인 쪽이 받는 방식  | `TickGenerator` → `TickSubscriber`    |
| 분산 락       | 같은 지갑을 동시에 건드리지 못하게 "한 줄 세우기"        | `SafeOrderExecutor`                   |
| 조건부 UPDATE | `where balance >= 금액` 처럼 검사와 변경을 한 문장으로   | `SafeExecutionTransaction`            |
| 멱등성        | 같은 요청을 여러 번 보내도 결과는 한 번만                | `OrderService.create`, DB UNIQUE 제약 |
| 메모리 인덱스 | 틱마다 DB를 읽으면 느려서 대기 주문을 메모리에 들고 있기 | `ActiveOrderIndex`                    |
| 배치 저장     | 1건씩 저장하지 않고 모아서 한꺼번에 저장                 | `BatchTickStore`                      |

---

## 5. 읽는 순서 (추천)

각 파일은 맨 위 주석에 "왜 이렇게 만들었는지"를 적어 두었다. **주석 먼저 읽고, 코드는 그 다음에.**

1. **`order/OrderController` → `OrderService` → `OrderRepository`** : 익숙한 3층. 여기서 자신감 얻기
2. **`order/TriggerOrder`** : DB 테이블이 클래스로 어떻게 보이는지
3. **`tick/TickGenerator`** : 시세를 만들어 Redis로 보내는 곳 (`@Scheduled` 처음 만남)
4. **`tick/TickSubscriber`** : 시세를 받아서 4곳으로 나눠 주는 곳
5. **`order/ActiveOrderIndex` → `execution/OrderMatcher`** : 조건 맞는 주문 찾기
6. **`execution/NaiveOrderExecutor`** : 일부러 틀리게 만든 버전. 주석의 "돈이 복사되는 이유"를 읽어 보기
7. **`execution/SafeOrderExecutor` → `SafeExecutionTransaction`** : 그걸 고친 버전. 6번과 비교하기
8. **`execution/SafeOrderExecutorTest`** : 고쳤다는 걸 증명하는 테스트

그 뒤에 `tick/BatchTickStore`(배치 저장), `wallet/CostBasis`(평균 매입가 계산)를 보면 된다.

---

## 6. 직접 해보면 이해가 빨라진다

읽기만 하면 잘 안 남는다. 작게 고쳐서 결과를 눈으로 확인해 보자.

1. **시세 속도 바꾸기**: `TickGenerator`의 `TICKS_PER_RUN = 100`을 `10`으로 바꾸고 백엔드를 재시작 → 화면 시세가 느려진다.
2. **간단한 API 추가**: `GET /api/ping`이 `{"ok":true}`를 돌려주는 컨트롤러를 만들어 보기 → Controller가 뭔지 손에 잡힌다.
3. **레이스 컨디션 직접 보기**: `EXECUTION_MODE=naive docker compose up -d backend` 후 `k6/README.md` 순서로 테스트 → 정합성이 깨지는 걸 숫자로 확인.
4. **관찰 화면으로 DB 지켜보기**: 브라우저에서 `http://localhost:3000/debug`를 열어 두고 다른 탭에서 주문을 넣어 보기 →
   `trigger_orders`/`executions` 행 수가 늘고, 최근 주문의 상태가 `대기 중 → 체결`로 바뀌고, 잔액과 정합성 검사가 갱신되는 게 보인다.
   (개발용 화면이라 로컬에서만 켜진다. 백엔드 코드는 `debug/DebugController`)
5. **로그 따라가기**: `docker compose logs -f backend` 를 켜 놓고 주문을 등록 → 어떤 코드가 실행되는지 로그로 추적.

---

## 7. 막힐 때

- 어떤 파일이 무슨 일을 하는지 헷갈리면 **그 파일 맨 위 주석**이 정답이다.
- 용어가 낯설면 위 사전에서 찾기.
- 그래도 모르겠으면 **파일 이름만 말해 주세요.** 그 파일을 줄 단위로 풀어서 설명해 줄 수 있다.
