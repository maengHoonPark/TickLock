package com.ticklock.debug;

import com.ticklock.order.OrderSide;
import com.ticklock.order.OrderStatus;
import com.ticklock.order.TriggerCondition;
import com.ticklock.wallet.DemoAccount;
import com.ticklock.wallet.HoldingService;
import com.ticklock.wallet.WalletAudit;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 🛠 개발용 관찰 API: "지금 DB와 파이프라인 안이 어떤 상태인지"를 한 번에 보여준다.
 *
 * 개발·학습할 때 화면과 DB를 번갈아 보는 수고를 줄이려고 만들었다.
 * 내부 상태를 그대로 노출하므로 ticklock.debug.enabled=true 일 때만 켜진다 (기본값은 꺼짐).
 * 실제 서비스에 배포할 때는 이 설정을 켜지 않으면 이 주소 자체가 존재하지 않는다(404).
 */
@RestController
@RequestMapping("/api/debug")
@ConditionalOnProperty(name = "ticklock.debug.enabled", havingValue = "true")
public class DebugController {

    /** 행 수를 셀 테이블 목록. 고정 목록(화이트리스트)만 SQL에 넣으므로 SQL 주입 위험이 없다. */
    private static final List<String> TABLES =
            List.of("users", "wallets", "trigger_orders", "executions", "holdings", "price_ticks");

    private static final int RECENT_LIMIT = 10;

    public record OrderRow(long id, String symbol, OrderSide side, TriggerCondition condition,
                           long targetPrice, long quantity, OrderStatus status, Instant createdAt) {
    }

    public record ExecutionRow(long id, long orderId, String symbol, OrderSide side,
                               long price, long quantity, Instant executedAt) {
    }

    public record WalletInfo(long balance, long expectedBalance, long difference, boolean consistent) {
    }

    /** 시세 파이프라인 지표. 값이 어떻게 변하는지 보면 병목이 어디인지 알 수 있다. */
    public record Pipeline(long published, long received, long stored, long dropped,
                           long storeFailed, long outOfOrder, long bufferSize) {
    }

    public record Snapshot(Instant serverTime, Map<String, Long> tableCounts, WalletInfo wallet,
                           List<HoldingService.HoldingView> holdings, List<OrderRow> recentOrders,
                           List<ExecutionRow> recentExecutions, Pipeline pipeline) {
    }

    private final JdbcTemplate jdbc;
    private final DemoAccount demoAccount;
    private final WalletAudit walletAudit;
    private final HoldingService holdingService;
    private final MeterRegistry meterRegistry;

    public DebugController(JdbcTemplate jdbc, DemoAccount demoAccount, WalletAudit walletAudit,
                           HoldingService holdingService, MeterRegistry meterRegistry) {
        this.jdbc = jdbc;
        this.demoAccount = demoAccount;
        this.walletAudit = walletAudit;
        this.holdingService = holdingService;
        this.meterRegistry = meterRegistry;
    }

    @GetMapping("/snapshot")
    public Snapshot snapshot() {
        WalletAudit.Result audit = walletAudit.run();
        return new Snapshot(
                Instant.now(),
                tableCounts(),
                new WalletInfo(audit.balance(), audit.expectedBalance(), audit.difference(), audit.consistent()),
                holdingService.list(),
                recentOrders(),
                recentExecutions(),
                pipeline());
    }

    private Map<String, Long> tableCounts() {
        // LinkedHashMap: 목록에 적은 순서대로 화면에 보여주려고 순서를 유지한다
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : TABLES) {
            counts.put(table, jdbc.queryForObject("select count(*) from " + table, Long.class));
        }
        return counts;
    }

    private List<OrderRow> recentOrders() {
        return jdbc.query("""
                select id, symbol, side, trigger_condition, target_price, quantity, status, created_at
                from trigger_orders where user_id = ? order by id desc limit ?
                """, (resultSet, rowNumber) -> new OrderRow(
                resultSet.getLong("id"),
                resultSet.getString("symbol"),
                OrderSide.valueOf(resultSet.getString("side")),
                TriggerCondition.valueOf(resultSet.getString("trigger_condition")),
                resultSet.getLong("target_price"),
                resultSet.getLong("quantity"),
                OrderStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("created_at").toInstant()),
                demoAccount.identity().userId(), RECENT_LIMIT);
    }

    private List<ExecutionRow> recentExecutions() {
        return jdbc.query("""
                select e.id, e.trigger_order_id, e.symbol, o.side, e.price, e.quantity, e.executed_at
                from executions e join trigger_orders o on o.id = e.trigger_order_id
                where e.wallet_id = ? order by e.id desc limit ?
                """, (resultSet, rowNumber) -> new ExecutionRow(
                resultSet.getLong("id"),
                resultSet.getLong("trigger_order_id"),
                resultSet.getString("symbol"),
                OrderSide.valueOf(resultSet.getString("side")),
                resultSet.getLong("price"),
                resultSet.getLong("quantity"),
                resultSet.getTimestamp("executed_at").toInstant()),
                demoAccount.identity().walletId(), RECENT_LIMIT);
    }

    private Pipeline pipeline() {
        return new Pipeline(
                counter("ticklock.ticks.published"),
                counter("ticklock.ticks.received"),
                counter("ticklock.ticks.stored"),
                counter("ticklock.ticks.dropped"),
                counter("ticklock.ticks.store_failed"),
                counter("ticklock.ticks.out_of_order"),
                gauge("ticklock.ticks.queue.size"));
    }

    private long counter(String name) {
        Counter counter = meterRegistry.find(name).counter();
        return counter == null ? 0 : (long) counter.count();
    }

    /** 게이지는 batch 저장 모드에서만 등록된다. 없으면 0으로 본다. */
    private long gauge(String name) {
        Gauge gauge = meterRegistry.find(name).gauge();
        return gauge == null ? 0 : (long) gauge.value();
    }
}
