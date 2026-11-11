package com.ticklock.tick;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 시세 관련 HTTP 진입점: 종목 목록(REST)과 실시간 스트림(SSE). */
@RestController
@RequestMapping("/api")
public class StreamController {

    /**
     * 화면 첫 렌더링용 종목 + 현재가.
     * basePrice는 "전일 종가" 역할의 기준가로, 프론트가 등락(원/%)을 계산하는 데 쓴다.
     */
    public record StockView(String symbol, String name, long price, long basePrice) {
    }

    private final StockCatalog stockCatalog;
    private final PriceCache priceCache;
    private final TickBroadcaster broadcaster;

    public StreamController(StockCatalog stockCatalog, PriceCache priceCache, TickBroadcaster broadcaster) {
        this.stockCatalog = stockCatalog;
        this.priceCache = priceCache;
        this.broadcaster = broadcaster;
    }

    @GetMapping("/stocks")
    public List<StockView> stocks() {
        List<StockView> views = new ArrayList<>();
        for (Stock stock : stockCatalog.all()) {
            // 캐시에 아직 시세가 없으면(서버 막 시작) 기준가를 보여준다
            Optional<Tick> latest = priceCache.find(stock.getSymbol());
            long price = latest.isPresent() ? latest.get().price() : stock.getBasePrice();
            views.add(new StockView(stock.getSymbol(), stock.getName(), price, stock.getBasePrice()));
        }
        return views;
    }

    /** 브라우저의 EventSource가 연결하는 곳. 연결을 끊지 않고 계속 열어두며 시세를 흘려보낸다. */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return broadcaster.subscribe();
    }
}
