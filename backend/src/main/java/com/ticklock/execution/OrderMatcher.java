package com.ticklock.execution;

import com.ticklock.order.ActiveOrder;
import com.ticklock.order.ActiveOrderIndex;
import com.ticklock.tick.StockCatalog;
import com.ticklock.tick.Tick;
import com.ticklock.tick.TickBroadcaster;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 시세가 들어올 때마다 "조건이 맞은 대기 주문"을 찾아 체결 스레드풀에 넘기는 역할.
 *
 * 왜 스레드풀로 넘기나?
 *  시세를 받는 스레드(Redis 구독 스레드)가 DB 작업으로 막히면 그동안 들어오는 시세가 밀린다.
 *  그래서 구독 스레드는 "판단 + 접수"만 하고, 느린 DB 작업은 별도 스레드들이 맡는다.
 *  (이 구조 때문에 같은 지갑의 주문이 여러 스레드에서 동시에 체결될 수 있고, 그래서 동시성 제어가 필요하다.)
 */
@Component
public class OrderMatcher {

    private static final Logger log = LoggerFactory.getLogger(OrderMatcher.class);

    private final ActiveOrderIndex index;
    private final OrderExecutor executor;
    private final TickBroadcaster broadcaster;
    private final ExecutorService workers;
    private final StockCatalog stockCatalog;

    public OrderMatcher(ActiveOrderIndex index, OrderExecutor executor, TickBroadcaster broadcaster,
                        StockCatalog stockCatalog,
                        @Value("${ticklock.execution.threads:8}") int threads) {
        this.index = index;
        this.executor = executor;
        this.broadcaster = broadcaster;
        this.stockCatalog = stockCatalog;
        this.workers = Executors.newFixedThreadPool(threads);
    }

    /** 시세 1건마다 호출된다. 이 종목의 대기 주문 중 조건이 맞은 것을 선점해 체결 스레드로 넘긴다. */
    public void onTick(Tick tick) {
        for (ActiveOrder order : index.findBySymbol(tick.symbol())) {
            // claim 성공 = 이 주문의 처리 책임이 나에게 있다. (실패 = 다른 스레드가 이미 가져갔다)
            if (order.isTriggeredBy(tick.price()) && index.claim(order)) {
                workers.submit(() -> executeSafely(order, tick.price()));
            }
        }
    }

    private void executeSafely(ActiveOrder order, long price) {
        try {
            ExecutionResult result = executor.execute(order, price);
            // 알림은 executor가 끝난 "뒤"에 보낸다. 트랜잭션이 커밋되기 전에 알리면 화면은 체결인데 DB는 아직일 수 있다.
            notifyResult(result);
        } catch (LockNotAcquiredException e) {
            // 일시적 혼잡: 주문을 실패시키지 않고 인덱스에 되돌려, 다음 시세 틱에서 자연스럽게 다시 시도되게 한다.
            log.warn("{} — 대기열에 되돌립니다. orderId={}", e.getMessage(), order.id());
            index.add(order);
        } catch (RuntimeException e) {
            // 한 주문의 실패가 다른 주문 처리를 멈추게 하면 안 된다. 재시도/보정 정책은 복원력 단계(P5)에서 다룬다.
            log.error("주문 체결 중 오류. orderId={}", order.id(), e);
        }
    }

    private void notifyResult(ExecutionResult result) {
        if (result instanceof ExecutionResult.Executed executed) {
            broadcaster.broadcast(OrderEvents.Executed.of(executed, nameOf(executed.symbol())));
        } else if (result instanceof ExecutionResult.Failed failed) {
            broadcaster.broadcast(OrderEvents.Failed.of(failed, nameOf(failed.symbol())));
        }
        // Skipped는 알릴 것이 없다
    }

    private String nameOf(String symbol) {
        return stockCatalog.nameOf(symbol);
    }

    @PreDestroy
    void shutdown() {
        workers.shutdown();
    }
}
