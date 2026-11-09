package com.ticklock.execution;

import com.ticklock.order.ActiveOrder;
import com.ticklock.order.OrderSide;
import com.ticklock.order.OrderStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * 락 "안쪽"에서 실행되는 DB 트랜잭션. 체결에 필요한 모든 변경이 이 안에서 전부 성공하거나 전부 취소된다.
 *
 * 왜 SafeOrderExecutor와 별도 클래스인가?
 *  @Transactional은 Spring이 객체를 감싸는 "프록시"로 동작해서, 같은 클래스 안의 메서드를 직접 호출하면 적용되지 않는다.
 *  락을 트랜잭션 바깥에 두려면(락 → 트랜잭션 시작 → 커밋 → 락 해제) 두 역할을 다른 빈으로 나눠야 한다.
 *
 * 핵심 기법: "읽고 계산해서 덮어쓰기"를 없애고, DB가 한 문장으로 검사와 변경을 같이 하게 한다.
 *   update wallets set balance = balance - ? where id = ? and balance >= ?
 *  → 잔액 확인과 차감이 원자적이라, 락이 어떤 이유로 뚫려도 잔액이 음수가 되거나 돈이 복사되지 않는다.
 */
@Component
public class SafeExecutionTransaction {

    private final JdbcTemplate jdbc;
    private final ExecutionLedger ledger;

    public SafeExecutionTransaction(JdbcTemplate jdbc, ExecutionLedger ledger) {
        this.jdbc = jdbc;
        this.ledger = ledger;
    }

    @Transactional
    public ExecutionResult execute(ActiveOrder order, long price) {
        return order.side() == OrderSide.BUY ? buy(order, price) : sell(order, price);
    }

    private ExecutionResult buy(ActiveOrder order, long price) {
        long amount = price * order.quantity();

        // ① 잔액이 충분할 때만 차감한다. 0행이면 잔액 부족.
        int debited = jdbc.update(
                "update wallets set balance = balance - ?, version = version + 1, updated_at = now() "
                        + "where id = ? and balance >= ?",
                amount, order.walletId(), amount);
        if (debited == 0) {
            return ledger.failIfStillActive(order, "잔액이 부족해요");
        }

        // ② 주문 상태를 ACTIVE → EXECUTED로 선점. 그 사이 취소된 주문이면 위 차감까지 통째로 되돌린다.
        if (!ledger.transition(order, OrderStatus.EXECUTED)) {
            return rollbackAsSkipped(order);
        }

        jdbc.update("""
                insert into holdings (wallet_id, symbol, quantity) values (?, ?, ?)
                on conflict (wallet_id, symbol) do update set quantity = holdings.quantity + excluded.quantity
                """, order.walletId(), order.symbol(), order.quantity());

        ledger.recordExecution(order, price);
        return ledger.executed(order, price);
    }

    private ExecutionResult sell(ActiveOrder order, long price) {
        long amount = price * order.quantity();

        // ① 보유 수량이 충분할 때만 차감한다 (마찬가지로 검사와 변경이 한 문장)
        int released = jdbc.update(
                "update holdings set quantity = quantity - ? where wallet_id = ? and symbol = ? and quantity >= ?",
                order.quantity(), order.walletId(), order.symbol(), order.quantity());
        if (released == 0) {
            return ledger.failIfStillActive(order, "보유 수량이 부족해요");
        }

        if (!ledger.transition(order, OrderStatus.EXECUTED)) {
            return rollbackAsSkipped(order);
        }

        jdbc.update("update wallets set balance = balance + ?, version = version + 1, updated_at = now() where id = ?",
                amount, order.walletId());

        ledger.recordExecution(order, price);
        return ledger.executed(order, price);
    }

    /** 예외를 던지지 않고 "이 트랜잭션은 커밋하지 말고 취소해 달라"고 표시한 뒤 건너뜀 결과를 돌려준다. */
    private ExecutionResult rollbackAsSkipped(ActiveOrder order) {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return new ExecutionResult.Skipped(order.id());
    }
}
