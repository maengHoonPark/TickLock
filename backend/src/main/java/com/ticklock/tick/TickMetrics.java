package com.ticklock.tick;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * 시세 파이프라인의 측정값 모음 (Micrometer). /actuator/metrics/{이름} 으로 조회한다.
 *
 * "측정하지 않은 최적화는 추측"이다. 저장 방식을 바꿀 때 처리량·지연·유실을 숫자로 비교하려고 만들었다.
 *  - published    : 생성기가 Redis에 발행한 건수
 *  - received     : 구독자가 받아 처리를 끝낸 건수  (published와의 차이 = 밀린 양)
 *  - stored       : DB에 저장된 건수
 *  - dropped      : 버퍼가 가득 차서 버린 건수
 *  - storeFailed  : DB 저장 실패로 잃은 건수
 *  - outOfOrder   : 발행 순서와 다르게 도착한 건수
 *  - lag          : 발행 시각 → 처리 완료까지 걸린 시간
 */
@Component
public class TickMetrics {

    private final Counter published;
    private final Counter received;
    private final Counter stored;
    private final Counter dropped;
    private final Counter storeFailed;
    private final Counter outOfOrder;
    private final Timer lag;

    public TickMetrics(MeterRegistry registry) {
        this.published = Counter.builder("ticklock.ticks.published").register(registry);
        this.received = Counter.builder("ticklock.ticks.received").register(registry);
        this.stored = Counter.builder("ticklock.ticks.stored").register(registry);
        this.dropped = Counter.builder("ticklock.ticks.dropped").register(registry);
        this.storeFailed = Counter.builder("ticklock.ticks.store_failed").register(registry);
        this.outOfOrder = Counter.builder("ticklock.ticks.out_of_order").register(registry);
        this.lag = Timer.builder("ticklock.ticks.lag").register(registry);
    }

    public void published() {
        published.increment();
    }

    public void received() {
        received.increment();
    }

    public void stored(int count) {
        stored.increment(count);
    }

    public void dropped() {
        dropped.increment();
    }

    public void storeFailed(int count) {
        storeFailed.increment(count);
    }

    public void outOfOrder() {
        outOfOrder.increment();
    }

    public void lag(long tickTimestampMillis) {
        lag.record(Duration.ofMillis(Math.max(0, System.currentTimeMillis() - tickTimestampMillis)));
    }
}
