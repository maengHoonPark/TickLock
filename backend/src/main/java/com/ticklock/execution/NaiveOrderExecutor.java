package com.ticklock.execution;

import com.ticklock.order.ActiveOrder;
import com.ticklock.order.OrderSide;
import com.ticklock.order.OrderStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ⚠️ 일부러 만든 "취약한" 체결기. 레이스 컨디션을 재현하기 위해 락 없이 구현했다. 실서비스에서 이렇게 쓰면 안 된다.
 * (ticklock.execution.mode=naive 로 켤 수 있다. 기본값은 안전한 SafeOrderExecutor.)
 *
 * 무엇이 문제인가? (Lost Update, 갱신 손실)
 *  같은 지갑으로 두 주문이 동시에 체결되는 상황을 보자. 잔액 100, 각 주문 비용 60.
 *    스레드 A: 잔액 읽음(100) → 충분하네 → 100-60=40 으로 저장
 *    스레드 B: 잔액 읽음(100) → 충분하네 → 100-60=40 으로 저장      ← A의 결과를 모르고 덮어씀
 *  결과: 120원어치를 샀는데 잔액은 40원(정답은 -20원 = 두 번째 주문은 실패했어야 함). 돈이 복사된 것이다.
 *
 * "@Transactional을 붙였는데 왜 안 막아주나?"
 *  기본 격리 수준(READ COMMITTED)에서 트랜잭션은 "중간 상태를 남에게 숨겨줄" 뿐, 두 트랜잭션이
 *  같은 값을 읽고 각자 계산해 덮어쓰는 것은 막지 못한다. 읽기와 쓰기 사이에 "줄 세우기(락)"가 필요하다.
 *
 * 취약한 지점은 아래 주석의 ①(읽기) → ②(읽은 값으로 계산해 덮어쓰기) 구간이다.
 */
@Component
@ConditionalOnProperty(name = "ticklock.execution.mode", havingValue = "naive")
public class NaiveOrderExecutor implements OrderExecutor {

    private final JdbcTemplate jdbc;
    private final ExecutionLedger ledger;

    public NaiveOrderExecutor(JdbcTemplate jdbc, ExecutionLedger ledger) {
        this.jdbc = jdbc;
        this.ledger = ledger;
    }

    @Override
    @Transactional
    public ExecutionResult execute(ActiveOrder order, long currentPrice) {
        return order.side() == OrderSide.BUY ? buy(order, currentPrice) : sell(order, currentPrice);
    }

    private ExecutionResult buy(ActiveOrder order, long price) {
        long amount = price * order.quantity();

        long balance = readBalance(order.walletId()); // ① 읽기 — 이 시점의 값이 곧 다른 스레드와 겹치는 지점
        if (balance < amount) {
            return ledger.failIfStillActive(order, "잔액이 부족해요");
        }
        if (!ledger.transition(order, OrderStatus.EXECUTED)) {
            return new ExecutionResult.Skipped(order.id());
        }

        // ② 읽어둔 값으로 새 잔액을 "계산해서" 덮어쓴다. 그 사이 다른 스레드가 바꾼 값은 사라진다.
        jdbc.update("update wallets set balance = ? where id = ?", balance - amount, order.walletId());
        jdbc.update("""
                insert into holdings (wallet_id, symbol, quantity) values (?, ?, ?)
                on conflict (wallet_id, symbol) do update set quantity = holdings.quantity + excluded.quantity
                """, order.walletId(), order.symbol(), order.quantity());

        ledger.recordExecution(order, price);
        return ledger.executed(order, price);
    }

    private ExecutionResult sell(ActiveOrder order, long price) {
        long amount = price * order.quantity();

        long held = readHolding(order.walletId(), order.symbol()); // ① 읽기
        if (held < order.quantity()) {
            return ledger.failIfStillActive(order, "보유 수량이 부족해요");
        }
        if (!ledger.transition(order, OrderStatus.EXECUTED)) {
            return new ExecutionResult.Skipped(order.id());
        }

        long balance = readBalance(order.walletId());
        // ② 읽은 값 기준으로 계산해 덮어쓰기 (보유 수량, 잔액 모두 같은 문제를 가진다)
        jdbc.update("update holdings set quantity = ? where wallet_id = ? and symbol = ?",
                held - order.quantity(), order.walletId(), order.symbol());
        jdbc.update("update wallets set balance = ? where id = ?", balance + amount, order.walletId());

        ledger.recordExecution(order, price);
        return ledger.executed(order, price);
    }

    private long readBalance(long walletId) {
        Long balance = jdbc.queryForObject("select balance from wallets where id = ?", Long.class, walletId);
        return balance == null ? 0 : balance;
    }

    private long readHolding(long walletId, String symbol) {
        return jdbc.query("select quantity from holdings where wallet_id = ? and symbol = ?",
                resultSet -> resultSet.next() ? resultSet.getLong(1) : 0L, walletId, symbol);
    }
}
