package com.ticklock.tick;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 접속 중인 브라우저(SSE)로 시세를 밀어주는 역할.
 *
 * 핵심 아이디어: "합치기(coalescing) + 배치 전송".
 *  초당 1,000건을 그대로 보내면 브라우저가 감당하지 못한다. 그래서
 *   1) 들어오는 틱은 종목별 "가장 최신 1건"만 pending에 덮어쓰고,
 *   2) 100ms마다 pending을 한꺼번에 비워 보낸다.
 *  → 종목 5개면 초당 최대 50건만 전송된다. 사용자는 100ms 이내의 중간 가격을 못 보지만 체감 차이는 없다.
 */
@Component
public class TickBroadcaster {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, Tick> pending = new ConcurrentHashMap<>();
    private final PriceCache priceCache;

    public TickBroadcaster(PriceCache priceCache) {
        this.priceCache = priceCache;
    }

    /** 새 브라우저 연결을 등록하고, 연결 직후 현재 시세 스냅샷을 먼저 보내 빈 화면을 없앤다. */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L); // 0 = 서버 쪽 타임아웃 없음
        emitters.add(emitter);
        // 연결이 어떤 이유로든 끝나면 목록에서 제거해야 메모리가 새지 않는다.
        // `() -> ...` 는 "나중에 이 일이 일어나면 이 코드를 실행해 달라"고 맡기는 콜백이다 (스트림과는 별개의 문법).
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));

        for (Tick tick : priceCache.snapshot()) {
            send(emitter, tick);
        }
        return emitter;
    }

    /** 최신 틱만 남기고 덮어쓴다. 실제 전송은 flush()가 일정 주기로 한다. */
    public void offer(Tick tick) {
        pending.put(tick.symbol(), tick);
    }

    @Scheduled(fixedRate = 100)
    void flush() {
        if (emitters.isEmpty()) {
            pending.clear(); // 보는 사람이 없으면 쌓아둘 이유가 없다
            return;
        }
        for (String symbol : pending.keySet()) {
            // remove로 꺼내오면 "꺼내기 + 비우기"가 한 번에 되어, 그 사이 들어온 새 틱을 잃지 않는다.
            Tick tick = pending.remove(symbol);
            if (tick != null) {
                for (SseEmitter emitter : emitters) {
                    send(emitter, tick);
                }
            }
        }
    }

    /** 체결 알림처럼 "합치지 않고 즉시" 모든 접속자에게 보내야 하는 이벤트용. */
    public void broadcast(Object event) {
        for (SseEmitter emitter : emitters) {
            sendJson(emitter, event);
        }
    }

    private void send(SseEmitter emitter, Tick tick) {
        sendJson(emitter, TickEvent.from(tick));
    }

    private void sendJson(SseEmitter emitter, Object payload) {
        try {
            emitter.send(SseEmitter.event().data(payload, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            // 브라우저를 닫는 등 연결이 끊긴 경우. 목록에서 빼고 넘어간다.
            emitters.remove(emitter);
        }
    }
}
