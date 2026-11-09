package com.ticklock.wallet;

import com.ticklock.tick.StockCatalog;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 보유 종목 조회. 수량은 holdings 테이블이 정답이고, 평균 매입가는 체결 기록을 순서대로 다시 계산해 구한다. */
@Service
public class HoldingService {

    public record HoldingView(String symbol, String name, long quantity, long averagePrice) {
    }

    private final JdbcTemplate jdbc;
    private final DemoAccount demoAccount;
    private final StockCatalog stockCatalog;

    public HoldingService(JdbcTemplate jdbc, DemoAccount demoAccount, StockCatalog stockCatalog) {
        this.jdbc = jdbc;
        this.demoAccount = demoAccount;
        this.stockCatalog = stockCatalog;
    }

    public List<HoldingView> list() {
        long walletId = demoAccount.identity().walletId();
        Map<String, CostBasis> costBasisBySymbol = replayExecutions(walletId);

        return jdbc.query("""
                select symbol, quantity from holdings
                where wallet_id = ? and quantity > 0
                order by symbol
                """, (resultSet, rowNumber) -> {
            String symbol = resultSet.getString("symbol");
            CostBasis costBasis = costBasisBySymbol.get(symbol);
            return new HoldingView(symbol, stockCatalog.nameOf(symbol), resultSet.getLong("quantity"),
                    costBasis == null ? 0 : costBasis.averagePrice());
        }, walletId);
    }

    /** 체결 기록을 오래된 순서대로 재생하며 종목별 평균 매입가를 쌓는다. 순서가 중요해서 id 순으로 읽는다. */
    private Map<String, CostBasis> replayExecutions(long walletId) {
        Map<String, CostBasis> bySymbol = new HashMap<>();
        jdbc.query("""
                select e.symbol, o.side, e.price, e.quantity
                from executions e join trigger_orders o on o.id = e.trigger_order_id
                where e.wallet_id = ?
                order by e.id
                """, resultSet -> {
            CostBasis costBasis = bySymbol.computeIfAbsent(resultSet.getString("symbol"), key -> new CostBasis());
            long quantity = resultSet.getLong("quantity");
            if ("BUY".equals(resultSet.getString("side"))) {
                costBasis.buy(quantity, resultSet.getLong("price"));
            } else {
                costBasis.sell(quantity);
            }
        }, walletId);
        return bySymbol;
    }
}
