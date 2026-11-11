package com.ticklock.tick;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticklock.execution.OrderMatcher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    /** 학습용 로그를 몇 건에 한 번 찍을지 (500이면 초당 약 2번). */
    private static final int LOG_EVERY = 500;

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

    /**
     * Redis에서 시세 메시지가 도착할 때마다 Spring이 이 메서드를 호출한다. (우리가 직접 호출하는 곳은 없다)
     * message = 방금 도착한 메시지 한 건. 그 내용(getBody)은 아래처럼 생긴 글자다.
     *   {"symbol":"005930","price":71741,"volume":19,"ts":1794406968773,"seq":5404}
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // 받은 메시지를 "글자 그대로" 꺼낸다 (바이트 → 글자)
            String raw = new String(message.getBody(), StandardCharsets.UTF_8);
            // 그 글자를 자바 객체(Tick)로 바꾼다. 이제 tick.price() 처럼 값을 꺼내 쓸 수 있다.
            Tick tick = objectMapper.readValue(raw, Tick.class);

            // [학습용 로그] 컨트롤러에서 값을 찍어 보듯, 받은 메시지를 터미널에 찍는다.
            // 시세는 초당 1,000건이라 전부 찍으면 화면이 넘쳐서 LOG_EVERY건에 한 번만 찍는다. 필요 없으면 지워도 된다.
            if (tick.seq() % LOG_EVERY == 0) {
                log.info("[시세 메시지 수신] 받은 글자 = {}", raw);
                log.info("[시세 메시지 수신] 자바 객체로 바꾼 결과 = {}", tick);
            }

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
