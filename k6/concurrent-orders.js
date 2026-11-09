// 동시성 테스트: 같은 지갑으로 "즉시 체결되는" 매수 주문을 한꺼번에 많이 넣는다.
//
// 시나리오
//  - 지갑 잔액 1억원. 주문 1건 = 삼성전자 100주 ≈ 720만원 → 정상이라면 약 13건만 체결되고 나머지는 잔액 부족으로 실패해야 한다.
//  - 목표가를 시세보다 훨씬 높게(LTE 1,000,000) 걸어 다음 틱에 곧바로 체결되게 한다.
//  - 체결은 여러 스레드가 동시에 처리하므로, 락이 없으면 같은 잔액을 여러 스레드가 동시에 읽고 덮어써 돈이 복사된다.
//
// 실행: k6/README.md 참고
import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const ORDERS = Number(__ENV.ORDERS || 100);

export const options = {
  scenarios: {
    burst: {
      executor: "shared-iterations",
      vus: ORDERS, // 주문 수만큼 가상 유저를 동시에 투입
      iterations: ORDERS, // 각 유저가 1건씩
      maxDuration: "30s",
    },
  },
};

// 실행마다 다른 키를 쓰기 위한 접두어 (같은 키면 멱등성 때문에 기존 주문이 반환된다)
const RUN_ID = Date.now();

export default function () {
  const response = http.post(
    `${BASE_URL}/api/orders`,
    JSON.stringify({
      symbol: "005930",
      side: "BUY",
      condition: "LTE",
      targetPrice: 1000000,
      quantity: 100,
    }),
    {
      headers: {
        "Content-Type": "application/json",
        "Idempotency-Key": `k6-${RUN_ID}-${__VU}-${__ITER}`,
      },
    },
  );
  check(response, { "주문 등록 201": (r) => r.status === 201 });
}

// 모든 주문이 등록된 뒤 체결이 끝날 때까지 기다렸다가 정합성을 검사한다.
export function teardown() {
  sleep(8);
  const audit = http.get(`${BASE_URL}/api/wallet/audit`).json();
  console.log("────────── 정합성 검사 ──────────");
  console.log(`체결 건수        : ${audit.executedCount}건`);
  console.log(`실제 잔액        : ${audit.balance.toLocaleString()}원`);
  console.log(`정답 잔액        : ${audit.expectedBalance.toLocaleString()}원 (초기 잔액 − 체결 금액 합)`);
  console.log(`차이             : ${audit.difference.toLocaleString()}원`);
  console.log(audit.consistent ? "✅ 정합성 OK" : "❌ 정합성 깨짐 (돈이 복사/유실됨)");
}
