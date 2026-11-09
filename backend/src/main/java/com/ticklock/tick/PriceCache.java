package com.ticklock.tick;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 종목별 "최신 체결"만 들고 있는 메모리 캐시.
 *
 * 왜 필요한가?
 *  - 현재가 조회/신규 접속자 초기 화면은 DB를 거치지 않고 여기서 바로 응답한다 (I/O 병목 방지).
 *  - 여러 스레드(Redis 수신 스레드, HTTP 스레드)가 동시에 접근하므로 ConcurrentHashMap을 쓴다.
 */
@Component
public class PriceCache {

    private final ConcurrentHashMap<String, Tick> latest = new ConcurrentHashMap<>();

    public void update(Tick tick) {
        latest.put(tick.symbol(), tick);
    }

    public Optional<Tick> find(String symbol) {
        return Optional.ofNullable(latest.get(symbol));
    }

    public Collection<Tick> snapshot() {
        return latest.values();
    }
}
