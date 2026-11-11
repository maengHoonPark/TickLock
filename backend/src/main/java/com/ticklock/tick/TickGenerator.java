package com.ticklock.tick;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 가상 시세 생성기. 100ms마다 100건 = 초당 약 1,000건의 체결 이벤트를 Redis 채널에 발행한다.
 *
 * 가격은 "랜덤 워크": 직전 가격에서 ±0.01% 이내로 조금씩 움직인다. 그래서 차트가 실제 주가처럼 이어진다.
 */
@Component
// 테스트에서는 시세가 멋대로 흐르면 결과가 흔들리므로 끌 수 있게 한다 (기본은 켜짐)
@ConditionalOnProperty(name = "ticklock.tick.generator.enabled", havingValue = "true", matchIfMissing = true)
public class TickGenerator {

    private static final Logger log = LoggerFactory.getLogger(TickGenerator.class);

    private static final int TICKS_PER_RUN = 100;
    private static final long MIN_PRICE = 100;
    // 1bp = 0.01%. 한 틱 최대 변동폭 ±0.01%.
    // 종목당 초당 약 200틱이 들어오므로, 이보다 크면 몇 초 만에 수% 움직여 실제 주가처럼 보이지 않는다.
    private static final long MAX_MOVE_BP = 1;

    private final StockRepository stockRepository;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final TickMetrics metrics;

    /** 종목별 직전 가격. 이 메서드는 스케줄러 스레드 하나에서만 실행되므로(fixedRate는 겹쳐 실행되지 않는다) 동기화가 필요 없다. */
    private final Map<String, Long> lastPrices = new HashMap<>();
    private List<String> symbols = List.of();
    private long sequence = 0;

    public TickGenerator(StockRepository stockRepository, StringRedisTemplate redis, ObjectMapper objectMapper,
                        TickMetrics metrics) {
        this.stockRepository = stockRepository;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @Scheduled(fixedRate = 100)
    void generate() {
        if (symbols.isEmpty() && !loadStocks()) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long now = Instant.now().toEpochMilli();

        for (int i = 0; i < TICKS_PER_RUN; i++) {
            String symbol = symbols.get(random.nextInt(symbols.size()));
            long price = nextPrice(symbol, random);
            lastPrices.put(symbol, price);
            publish(new Tick(symbol, price, random.nextLong(1, 100), now, ++sequence));
        }
    }

    /** 가격을 직전 가격 기준 ±MAX_MOVE_BP 범위에서 무작위로 움직인다. */
    private long nextPrice(String symbol, ThreadLocalRandom random) {
        long previous = lastPrices.get(symbol);
        long moveBp = random.nextLong(-MAX_MOVE_BP, MAX_MOVE_BP + 1);
        long next = previous + previous * moveBp / 10_000;
        return Math.max(MIN_PRICE, next);
    }

    private void publish(Tick tick) {
        try {
            // DB에 바로 쓰지 않고 Redis 채널로 흘려보낸다. 수신 쪽(TickSubscriber)이 캐시/전송을 맡는다.
            redis.convertAndSend(TickChannel.NAME, objectMapper.writeValueAsString(tick));
            metrics.published();
        } catch (JsonProcessingException e) {
            // record 직렬화가 실패할 일은 사실상 없다. 그래도 조용히 삼키지 않고 기록한다.
            log.error("tick 직렬화 실패: {}", tick, e);
        }
    }

    /** 종목 마스터를 읽어 시작 가격을 세팅한다. DB가 아직 준비되지 않았으면 다음 주기에 다시 시도한다. */
    private boolean loadStocks() {
        List<Stock> stocks = stockRepository.findAll();
        if (stocks.isEmpty()) {
            return false;
        }
        List<String> loadedSymbols = new ArrayList<>();
        for (Stock stock : stocks) {
            lastPrices.put(stock.getSymbol(), stock.getBasePrice());
            loadedSymbols.add(stock.getSymbol());
        }
        symbols = loadedSymbols;
        return true;
    }
}
