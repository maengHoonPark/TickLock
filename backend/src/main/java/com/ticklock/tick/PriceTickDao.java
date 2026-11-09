package com.ticklock.tick;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * price_ticks 테이블 접근. 저장 전략(direct/batch)이 달라도 SQL은 한 곳에서만 관리한다.
 * 대량 INSERT가 목적이라 JPA 엔티티 대신 JdbcTemplate을 쓴다 (엔티티 생성·변경 감지 비용이 없다).
 */
@Component
public class PriceTickDao {

    private static final String INSERT_SQL = "insert into price_ticks (symbol, price, volume, ts) values (?, ?, ?, ?)";
    private static final int JDBC_BATCH_SIZE = 1_000;

    private final JdbcTemplate jdbc;

    public PriceTickDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 1건 저장. 호출할 때마다 DB와 왕복 1회 + 자동 커밋 1회가 일어난다. */
    public void insert(Tick tick) {
        jdbc.update(INSERT_SQL, tick.symbol(), tick.price(), tick.volume(), timestampOf(tick));
    }

    /**
     * 여러 건을 한 번에 저장. JDBC 드라이버가 reWriteBatchedInserts=true 설정으로
     * 여러 INSERT를 "다중 행 INSERT 한 문장"으로 합쳐 보내므로 왕복과 커밋 횟수가 크게 줄어든다.
     */
    public void insertAll(List<Tick> ticks) {
        jdbc.batchUpdate(INSERT_SQL, ticks, JDBC_BATCH_SIZE, (statement, tick) -> {
            statement.setString(1, tick.symbol());
            statement.setLong(2, tick.price());
            statement.setLong(3, tick.volume());
            statement.setTimestamp(4, timestampOf(tick));
        });
    }

    public int deleteOlderThan(Instant cutoff) {
        return jdbc.update("delete from price_ticks where ts < ?", Timestamp.from(cutoff));
    }

    private static Timestamp timestampOf(Tick tick) {
        return Timestamp.from(Instant.ofEpochMilli(tick.ts()));
    }
}
