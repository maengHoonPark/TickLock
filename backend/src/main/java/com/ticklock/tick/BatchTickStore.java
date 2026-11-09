package com.ticklock.tick;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 시세를 메모리 버퍼에 모았다가 1초마다 한꺼번에 INSERT한다.
 *
 * 핵심 아이디어 두 가지
 *  1) 수신 스레드는 "버퍼에 넣기"만 한다(마이크로초). DB가 잠깐 느려도 시세 수신/전송이 막히지 않는다.
 *  2) DB에는 초당 수백~수천 번의 왕복 대신, 다중 행 INSERT 몇 번으로 끝낸다.
 *
 * 버퍼는 크기를 제한한다(bounded). 제한이 없으면 DB가 오래 죽었을 때 메모리가 끝없이 차서 서버 전체가 죽는다.
 * 시세 로그는 일부 유실이 허용되는 데이터라서, 가득 차면 새 틱을 버리고 dropped 지표로 남긴다.
 * (돈이 걸린 데이터 — 주문/체결 — 에는 절대 이런 "버리기" 전략을 쓰지 않는다.)
 */
@Component
@ConditionalOnProperty(name = "ticklock.tick.storage.mode", havingValue = "batch", matchIfMissing = true)
public class BatchTickStore implements TickStore {

    private static final Logger log = LoggerFactory.getLogger(BatchTickStore.class);

    private static final int QUEUE_CAPACITY = 100_000;
    private static final int MAX_BATCH_SIZE = 5_000;

    private final BlockingQueue<Tick> buffer = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final PriceTickDao dao;
    private final TickMetrics metrics;

    public BatchTickStore(PriceTickDao dao, TickMetrics metrics, MeterRegistry registry) {
        this.dao = dao;
        this.metrics = metrics;
        Gauge.builder("ticklock.ticks.queue.size", buffer, BlockingQueue::size).register(registry);
    }

    @Override
    public void save(Tick tick) {
        // offer는 가득 차도 기다리지 않고 즉시 false를 돌려준다 → 수신 스레드가 절대 막히지 않는다
        if (!buffer.offer(tick)) {
            metrics.dropped();
        }
    }

    /** 이전 flush가 끝난 "뒤" 1초 쉬고 다음을 시작한다(fixedDelay). 겹쳐서 실행되지 않는다. */
    @Scheduled(fixedDelay = 1_000)
    void flush() {
        List<Tick> batch = new ArrayList<>(MAX_BATCH_SIZE);
        // 한 번에 다 비우지 않고 MAX_BATCH_SIZE씩 나눠서 처리해, 한 번의 INSERT가 너무 커지지 않게 한다
        while (buffer.drainTo(batch, MAX_BATCH_SIZE) > 0) {
            writeBatch(batch);
            batch.clear();
        }
    }

    private void writeBatch(List<Tick> batch) {
        try {
            dao.insertAll(batch);
            metrics.stored(batch.size());
        } catch (DataAccessException e) {
            // DB 오류로 이번 묶음은 잃는다. 시세 로그라 재시도로 서버를 더 바쁘게 만들기보다 기록하고 넘어간다.
            metrics.storeFailed(batch.size());
            log.error("시세 {}건 저장 실패", batch.size(), e);
        }
    }

    /** 서버를 끌 때 버퍼에 남은 시세를 마저 저장한다. */
    @PreDestroy
    void flushRemaining() {
        flush();
    }
}
