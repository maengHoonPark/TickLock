package com.ticklock.wallet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 정합성 검사: "체결 내역을 처음부터 다시 계산한 잔액"과 "지금 DB에 있는 잔액"이 같은가?
 *
 * 돈은 사라지거나 복사되면 안 된다. 그래서 정답은 항상
 *   초기 잔액 − (매수 체결 금액 합) + (매도 체결 금액 합)
 * 으로 체결 기록(executions)만 보고 독립적으로 계산할 수 있다. 이 값과 실제 잔액이 다르면 동시성 버그다.
 */
@Component
public class WalletAudit {

    public record Result(long balance, long expectedBalance, long difference, long executedCount) {

        public boolean consistent() {
            return difference == 0;
        }
    }

    private final JdbcTemplate jdbc;
    private final DemoAccount demoAccount;
    private final long initialBalance;

    public WalletAudit(JdbcTemplate jdbc, DemoAccount demoAccount,
                       @Value("${ticklock.demo.initial-balance}") long initialBalance) {
        this.jdbc = jdbc;
        this.demoAccount = demoAccount;
        this.initialBalance = initialBalance;
    }

    public Result run() {
        long walletId = demoAccount.identity().walletId();
        long balance = jdbc.queryForObject("select balance from wallets where id = ?", Long.class, walletId);

        // 매수는 잔액이 줄고 매도는 늘어난다
        long netSpent = jdbc.queryForObject("""
                select coalesce(sum(case when o.side = 'BUY' then e.price * e.quantity
                                         else -(e.price * e.quantity) end), 0)
                from executions e join trigger_orders o on o.id = e.trigger_order_id
                where e.wallet_id = ?
                """, Long.class, walletId);
        long executedCount = jdbc.queryForObject("select count(*) from executions where wallet_id = ?",
                Long.class, walletId);

        long expected = initialBalance - netSpent;
        return new Result(balance, expected, balance - expected, executedCount);
    }
}
