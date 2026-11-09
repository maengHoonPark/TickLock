package com.ticklock.execution;

import com.ticklock.order.ActiveOrder;

/**
 * 조건이 충족된 주문 1건을 실제로 체결하는 전략.
 * 구현을 둘로 나눠 설정(ticklock.execution.mode)으로 갈아 끼운다.
 *  - naive : 락 없이 구현한 "일부러 취약한" 버전 → 레이스 컨디션 재현용 (P3)
 *  - safe  : Redisson 락 + DB 안전장치 버전 → 문제 해결 (P4)
 * 같은 부하 테스트를 두 구현에 돌려 "문제 → 재현 → 해결"을 숫자로 비교하기 위한 구조다.
 */
public interface OrderExecutor {

    ExecutionResult execute(ActiveOrder order, long currentPrice);
}
