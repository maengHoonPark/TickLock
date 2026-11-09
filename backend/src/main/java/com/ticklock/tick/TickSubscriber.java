package com.ticklock.tick;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticklock.execution.OrderMatcher;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Redis "ticks" 채널의 수신자.
 *
 * 받은 시세로 하는 일은 네 가지다.
 *  1) 최신가 캐시 갱신
 *  2) 브라우저 전송 대기열에 등록
 *  3) 조건이 맞은 대기 주문이 있는지 확인(OrderMatcher)
 *  4) 시세 로그 저장(TickStore: 즉시 저장 또는 배치 저장)
 */
@Component
public class TickSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(TickSubscriber.class);

    private final ObjectMapper objectMapper;
    private final PriceCache priceCache;
    private final TickBroadcaster broadcaster;
    private final OrderMatcher orderMatcher;
    private final TickStore tickStore;
    private final TickMetrics metrics;

    /** 지금까지 받은 가장 큰 발행 순번. 이보다 작은 순번이 오면 "순서가 뒤바뀌어 도착한 것"이다. */
    private final AtomicLong highestSeq = new AtomicLong();

    public TickSubscriber(ObjectMapper objectMapper, PriceCache priceCache, TickBroadcaster broadcaster,
                          OrderMatcher orderMatcher, TickStore tickStore, TickMetrics metrics) {
        this.objectMapper = objectMapper;
        this.priceCache = priceCache;
        this.broadcaster = broadcaster;
        this.orderMatcher = orderMatcher;
        this.tickStore = tickStore;
        this.metrics = metrics;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            Tick tick = objectMapper.readValue(message.getBody(), Tick.class);
            if (tick.seq() < highestSeq.getAndAccumulate(tick.seq(), Math::max)) {
                metrics.outOfOrder();
            }
            priceCache.update(tick);
            broadcaster.offer(tick);
            orderMatcher.onTick(tick);
            tickStore.save(tick);
            metrics.received();
            metrics.lag(tick.ts()); // 발행 → 처리 완료까지 걸린 시간
        } catch (IOException e) {
            // 이상한 메시지 하나가 구독 전체를 멈추게 하면 안 되므로, 기록만 하고 다음 메시지로 넘어간다.
            log.warn("해석할 수 없는 tick 메시지를 무시합니다", e);
        }
    }
}
