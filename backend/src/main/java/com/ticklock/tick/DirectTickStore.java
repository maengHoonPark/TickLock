package com.ticklock.tick;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * ⚠️ 비교용 "나쁜 예": 시세 1건마다 DB에 바로 INSERT한다.
 *
 * 왜 느린가? 한 건마다 (네트워크 왕복 + 트랜잭션 커밋 + WAL 디스크 동기화)를 치른다.
 * 그 일을 시세를 받는 스레드가 직접 하므로, DB가 1ms만 느려도 초당 1,000건을 따라가지 못하고 뒤로 밀리기 시작한다.
 */
@Component
@ConditionalOnProperty(name = "ticklock.tick.storage.mode", havingValue = "direct")
public class DirectTickStore implements TickStore {

    private final PriceTickDao dao;
    private final TickMetrics metrics;

    public DirectTickStore(PriceTickDao dao, TickMetrics metrics) {
        this.dao = dao;
        this.metrics = metrics;
    }

    @Override
    public void save(Tick tick) {
        dao.insert(tick);
        metrics.stored(1);
    }
}
