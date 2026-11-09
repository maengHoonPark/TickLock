package com.ticklock.execution;

import com.ticklock.order.OrderSide;
import com.ticklock.tick.StockCatalog;
import com.ticklock.wallet.DemoAccount;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 체결 내역 조회 (최신순). */
@RestController
@RequestMapping("/api/executions")
public class ExecutionController {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    public record ExecutionView(long id, long orderId, String symbol, String name, OrderSide side,
                                long price, long quantity, Instant executedAt) {
    }

    private final JdbcTemplate jdbc;
    private final DemoAccount demoAccount;
    private final StockCatalog stockCatalog;

    public ExecutionController(JdbcTemplate jdbc, DemoAccount demoAccount, StockCatalog stockCatalog) {
        this.jdbc = jdbc;
        this.demoAccount = demoAccount;
        this.stockCatalog = stockCatalog;
    }

    @GetMapping
    public List<ExecutionView> list(@RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        // 한 번에 너무 많이 가져가지 못하게 상한을 둔다 (잘못된 값이 들어와도 서버가 무거워지지 않게)
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        return jdbc.query("""
                select e.id, e.trigger_order_id, e.symbol, o.side, e.price, e.quantity, e.executed_at
                from executions e join trigger_orders o on o.id = e.trigger_order_id
                where e.wallet_id = ?
                order by e.id desc
                limit ?
                """, (resultSet, rowNumber) -> new ExecutionView(
                resultSet.getLong("id"),
                resultSet.getLong("trigger_order_id"),
                resultSet.getString("symbol"),
                stockCatalog.nameOf(resultSet.getString("symbol")),
                OrderSide.valueOf(resultSet.getString("side")),
                resultSet.getLong("price"),
                resultSet.getLong("quantity"),
                resultSet.getTimestamp("executed_at").toInstant()),
                demoAccount.identity().walletId(), safeLimit);
    }
}
