package com.ticklock.execution;

import com.ticklock.order.ActiveOrder;
import com.ticklock.order.OrderStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 체결기 구현(naive/safe)이 공통으로 쓰는 "주문 상태 + 체결 기록" 처리.
 * 두 구현의 차이는 "지갑 잔액을 어떻게 다루느냐"뿐이라, 나머지는 여기로 모아 중복을 없앴다.
 */
@Component
public class ExecutionLedger {

    private final JdbcTemplate jdbc;

    public ExecutionLedger(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 주문 상태를 ACTIVE → next로 "조건부" 변경한다. 0행이면 이미 취소/처리된 주문이라는 뜻이다.
     * 한 줄 UPDATE라서 "확인"과 "변경"이 원자적으로 일어난다 — 같은 주문을 동시에 처리하려는 시도 중 하나만 성공한다.
     */
    public boolean transition(ActiveOrder order, OrderStatus next) {
        return jdbc.update("update trigger_orders set status = ? where id = ? and status = 'ACTIVE'",
                next.name(), order.id()) == 1;
    }

    /** 조건은 맞았지만 체결할 수 없을 때: 아직 ACTIVE인 경우에만 FAILED로 종료시킨다. */
    public ExecutionResult failIfStillActive(ActiveOrder order, String reason) {
        return transition(order, OrderStatus.FAILED)
                ? new ExecutionResult.Failed(order.id(), order.symbol(), reason)
                : new ExecutionResult.Skipped(order.id());
    }

    /** 체결 기록 저장. executions.trigger_order_id UNIQUE라서 같은 주문의 이중 기록은 DB가 거부한다. */
    public void recordExecution(ActiveOrder order, long price) {
        jdbc.update("insert into executions (trigger_order_id, wallet_id, symbol, price, quantity) values (?, ?, ?, ?, ?)",
                order.id(), order.walletId(), order.symbol(), price, order.quantity());
    }

    public ExecutionResult.Executed executed(ActiveOrder order, long price) {
        return new ExecutionResult.Executed(order.id(), order.symbol(), order.side(), price, order.quantity());
    }
}
