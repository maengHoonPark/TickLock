package com.ticklock.tick;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 오래된 시세 로그 삭제. 초당 1,000건이면 하루에 8,600만 행이라 보관 기간을 정하지 않으면 디스크가 금방 찬다.
 *
 * 운영 환경에서는 행 단위 DELETE 대신 "시간별/일별 파티션 테이블"을 두고 오래된 파티션을 통째로 DROP하는 것이
 * 훨씬 빠르고 DB 부담이 적다. 여기서는 학습·로컬 용도라 단순한 주기 삭제로 두었다.
 */
@Component
public class TickRetention {

    private static final Logger log = LoggerFactory.getLogger(TickRetention.class);

    private final PriceTickDao dao;
    private final Duration retention;

    public TickRetention(PriceTickDao dao, @Value("${ticklock.tick.retention-minutes:10}") long retentionMinutes) {
        this.dao = dao;
        this.retention = Duration.ofMinutes(retentionMinutes);
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    void deleteExpired() {
        int deleted = dao.deleteOlderThan(Instant.now().minus(retention));
        if (deleted > 0) {
            log.debug("보관 기간이 지난 시세 {}건을 삭제했습니다.", deleted);
        }
    }
}
