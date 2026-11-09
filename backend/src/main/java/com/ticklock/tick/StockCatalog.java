package com.ticklock.tick;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 종목 마스터의 메모리 캐시. 종목 목록은 사실상 바뀌지 않는데, 주문 등록·조회·알림마다 DB에서 읽으면
 * 요청이 몰릴 때 DB 커넥션을 쓸데없이 점유한다. 처음 한 번만 읽고 메모리에서 답한다.
 *
 * 한계: 종목이 추가/변경되어도 서버를 다시 시작하기 전에는 반영되지 않는다.
 * (종목 관리 기능이 생기면 변경 시점에 캐시를 비우는 로직이 필요하다.)
 */
@Component
public class StockCatalog {

    private final StockRepository stockRepository;

    /** volatile: 한 스레드가 채운 맵을 다른 스레드가 즉시 보게 한다. 맵은 만든 뒤 바꾸지 않는다(불변). */
    private volatile Map<String, Stock> stocks;

    public StockCatalog(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    public boolean exists(String symbol) {
        return stocks().containsKey(symbol);
    }

    /** 종목명. 모르는 종목이면 코드를 그대로 돌려준다. */
    public String nameOf(String symbol) {
        Stock stock = stocks().get(symbol);
        return stock == null ? symbol : stock.getName();
    }

    public Collection<Stock> all() {
        return stocks().values();
    }

    private Map<String, Stock> stocks() {
        Map<String, Stock> loaded = stocks;
        if (loaded == null) {
            // 동시에 여러 스레드가 처음 호출해도 결과는 같다(같은 DB 내용). 한 번 더 읽는 비용만 들고 잘못되지는 않는다.
            // LinkedHashMap: DB에서 읽은 순서(= 화면에 보여줄 종목 순서)를 그대로 유지한다. 일반 HashMap은 순서가 뒤섞인다.
            loaded = Collections.unmodifiableMap(stockRepository.findAll().stream()
                    .collect(Collectors.toMap(Stock::getSymbol, Function.identity(), (first, second) -> first,
                            LinkedHashMap::new)));
            stocks = loaded;
        }
        return loaded;
    }
}
