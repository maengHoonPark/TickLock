package com.ticklock.execution;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticklock.order.ActiveOrder;
import com.ticklock.order.OrderSide;
import com.ticklock.order.TriggerCondition;
import com.ticklock.wallet.DemoAccount;
import com.ticklock.wallet.Wallet;
import com.ticklock.wallet.WalletAudit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 안전한 체결기의 동시성/멱등성 검증 (실제 PostgreSQL + Redis를 사용하는 통합 테스트).
 *
 * 핵심 기법: 모든 스레드를 CountDownLatch에 세워 두었다가 "동시에" 출발시킨다.
 * 그래야 우연히 순차 실행되는 것을 막고, 실제로 겹치는 상황을 만들 수 있다.
 *
 * 개발용 DB를 지우지 않도록 테스트는 별도 DB(ticklock_test)에서 돌린다. (k6/README.md 참고)
 */
@SpringBootTest(properties = {
        "ticklock.execution.mode=safe",
        "ticklock.tick.generator.enabled=false" // 시세가 멋대로 흐르면 결과가 흔들리므로 끈다
})
class SafeOrderExecutorTest {

    private static final String SYMBOL = "005930";
    private static final long PRICE = 70_000;
    private static final long QUANTITY = 100; // 주문 1건 = 700만원
    private static final long INITIAL_BALANCE = 100_000_000;

    @Autowired
    private OrderExecutor executor;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DemoAccount demoAccount;
    @Autowired
    private WalletAudit walletAudit;

    @BeforeEach
    void resetData() {
        // 자식 테이블(FK로 참조하는 쪽)부터 지운다
        jdbc.update("delete from executions");
        jdbc.update("delete from trigger_orders");
        jdbc.update("delete from holdings");
        jdbc.update("update wallets set balance = ?", INITIAL_BALANCE);
    }

    @Test
    @DisplayName("같은 지갑으로 50건이 동시에 체결돼도 잔액이 정확하고, 살 수 있는 만큼만 체결된다")
    void concurrentBuysKeepBalanceConsistent() throws Exception {
        List<ActiveOrder> orders = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            orders.add(insertOrder(OrderSide.BUY, "buy-" + i));
        }

        List<ExecutionResult> results = runConcurrently(orders);

        long executed = count(results, ExecutionResult.Executed.class);
        long failed = count(results, ExecutionResult.Failed.class);
        long affordable = INITIAL_BALANCE / (PRICE * QUANTITY); // 1억 / 700만 = 14건

        assertThat(executed).isEqualTo(affordable);
        assertThat(failed).isEqualTo(50 - affordable);
        assertThat(balance()).isEqualTo(INITIAL_BALANCE - affordable * PRICE * QUANTITY);
        assertThat(walletAudit.run().consistent()).isTrue();
    }

    @Test
    @DisplayName("같은 주문을 동시에 20번 체결 시도해도 한 번만 체결된다 (멱등성)")
    void sameOrderExecutesOnlyOnce() throws Exception {
        ActiveOrder order = insertOrder(OrderSide.BUY, "duplicate");

        List<ExecutionResult> results = runConcurrently(java.util.Collections.nCopies(20, order));

        assertThat(count(results, ExecutionResult.Executed.class)).isEqualTo(1);
        assertThat(count(results, ExecutionResult.Skipped.class)).isEqualTo(19);
        assertThat(jdbc.queryForObject("select count(*) from executions", Long.class)).isEqualTo(1);
        assertThat(balance()).isEqualTo(INITIAL_BALANCE - PRICE * QUANTITY); // 돈도 한 번만 빠졌다
    }

    @Test
    @DisplayName("보유 수량보다 많은 매도 주문이 동시에 와도 가진 만큼만 팔린다")
    void concurrentSellsCannotExceedHoldings() throws Exception {
        long walletId = demoAccount.wallet().getId();
        jdbc.update("insert into holdings (wallet_id, symbol, quantity) values (?, ?, ?)", walletId, SYMBOL, QUANTITY);

        List<ActiveOrder> orders = List.of(
                insertOrder(OrderSide.SELL, "sell-1"),
                insertOrder(OrderSide.SELL, "sell-2"),
                insertOrder(OrderSide.SELL, "sell-3"));

        List<ExecutionResult> results = runConcurrently(orders);

        assertThat(count(results, ExecutionResult.Executed.class)).isEqualTo(1);
        assertThat(count(results, ExecutionResult.Failed.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select quantity from holdings where wallet_id = ?", Long.class, walletId))
                .isZero();
        assertThat(balance()).isEqualTo(INITIAL_BALANCE + PRICE * QUANTITY);
        assertThat(walletAudit.run().consistent()).isTrue();
    }

    /** 모든 작업을 래치에 세워 두었다가 한꺼번에 출발시켜 실행하고, 결과를 모아 돌려준다. */
    private List<ExecutionResult> runConcurrently(List<ActiveOrder> orders) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(orders.size());
        CountDownLatch ready = new CountDownLatch(orders.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ExecutionResult>> futures = new ArrayList<>();
            for (ActiveOrder order : orders) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(); // 모두 준비될 때까지 대기
                    return executor.execute(order, PRICE);
                }));
            }
            ready.await();
            start.countDown(); // 출발!

            List<ExecutionResult> results = new ArrayList<>();
            for (Future<ExecutionResult> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private ActiveOrder insertOrder(OrderSide side, String requestKey) {
        Wallet wallet = demoAccount.wallet();
        Long id = jdbc.queryForObject("""
                insert into trigger_orders (user_id, wallet_id, symbol, side, trigger_condition,
                                            target_price, quantity, status, request_key)
                values (?, ?, ?, ?, 'LTE', ?, ?, 'ACTIVE', ?) returning id
                """, Long.class, wallet.getUserId(), wallet.getId(), SYMBOL, side.name(), PRICE, QUANTITY, requestKey);
        return new ActiveOrder(id, wallet.getId(), SYMBOL, side, TriggerCondition.LTE, PRICE, QUANTITY);
    }

    private long balance() {
        return jdbc.queryForObject("select balance from wallets where id = ?", Long.class, demoAccount.wallet().getId());
    }

    private static long count(List<ExecutionResult> results, Class<? extends ExecutionResult> type) {
        return results.stream().filter(type::isInstance).count();
    }
}
