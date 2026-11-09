# 동시성 부하 테스트 (k6)

같은 지갑으로 주문 100건이 동시에 체결될 때 잔액이 정확한지 검사한다.

## 실행 순서

1. 서버 켜기: `docker compose up -d`
2. 데이터 초기화:
   ```bash
   docker compose exec -T postgres psql -U ticklock -d ticklock < k6/reset.sql
   ```
3. 부하 테스트 실행 (k6를 로컬에 설치하지 않고 Docker로 실행):
   ```bash
   docker run --rm -i -e BASE_URL=http://host.docker.internal:8080 grafana/k6 run - < k6/concurrent-orders.js
   ```
4. 마지막에 출력되는 "정합성 검사"에서 `차이`가 0이면 정상, 0이 아니면 돈이 복사/유실된 것이다.
   언제든 `curl localhost:8080/api/wallet/audit`로 직접 확인할 수 있다.

## 체결 모드 바꾸기

- `safe`  : Redisson 락 + DB 조건부 UPDATE (기본값)
- `naive` : 락 없는 취약한 구현 (레이스 컨디션 재현용)

환경변수로 바꿔서 백엔드를 다시 띄운다. (초기화 → 테스트 순서는 위와 같다)
```bash
EXECUTION_MODE=naive docker compose up -d backend
```
```bash
docker compose up -d backend
```
두 번째 명령은 환경변수 없이 실행하는 것이라 기본값 `safe`로 돌아온다.

## 결과 비교 (주문 100건 동시 체결, 지갑 잔액 1억, 주문당 약 720만원)

| 모드 | 체결 건수 | 정답 건수 | 실제 잔액 | 정답 잔액 | 차이 |
|---|---|---|---|---|---|
| naive (락 없음) | **43건** | 약 13건 | 7,001,300원 | −207,602,200원 | **214,603,500원** ❌ |
| safe (락 + 조건부 UPDATE) | **13건** | 13건 | 6,488,200원 | 6,488,200원 | **0원** ✅ |

## 자동화 테스트 (JUnit, 실제 PostgreSQL + Redis)

개발 DB를 지우지 않도록 테스트 전용 DB에서 돌린다. 백엔드 컨테이너가 같은 빌드 폴더를 쓰므로 먼저 멈추고 실행한다.
```bash
docker compose stop backend
```
```bash
docker compose exec -T postgres psql -U ticklock -d postgres -c "create database ticklock_test"
```
```bash
docker compose run --rm --no-deps -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/ticklock_test backend gradle test --no-daemon
```
(DB가 이미 있으면 두 번째 명령은 "already exists" 오류를 내지만 무시해도 된다.)

검증 내용: ① 같은 지갑 50건 동시 매수 시 잔액 정확 ② 같은 주문 20번 동시 체결 시 1번만 체결 ③ 보유 수량 초과 동시 매도 차단.
