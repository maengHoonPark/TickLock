package com.ticklock.order;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 종목별 "대기 중(ACTIVE) 주문" 메모리 인덱스.
 *
 * 왜 필요한가?
 *  시세는 초당 1,000건이 들어온다. 틱마다 "이 종목의 대기 주문이 뭐지?"를 DB에 물으면 그것이 곧 I/O 병목이다.
 *  그래서 서버 시작 시 DB의 대기 주문을 메모리에 올려두고, 주문 등록/취소/체결 때만 갱신한다.
 *
 * claim()이 핵심: "이 주문을 내가 처리한다"를 원자적으로 선점한다. 여러 스레드가 동시에 같은 주문을
 * 발견해도 claim에 성공하는 쪽은 딱 하나라서, 같은 주문이 두 번 디스패치되지 않는다.
 */
@Component
public class ActiveOrderIndex {

    private static final Logger log = LoggerFactory.getLogger(ActiveOrderIndex.class);

    private final OrderRepository orderRepository;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<ActiveOrder>> bySymbol = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ActiveOrder> byId = new ConcurrentHashMap<>();

    public ActiveOrderIndex(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /** 서버가 완전히 뜬 직후, DB에 남아 있던 대기 주문을 메모리로 복구한다 (재시작해도 주문이 유실되지 않게). */
    @EventListener(ApplicationReadyEvent.class)
    void loadFromDatabase() {
        List<TriggerOrder> active = orderRepository.findByStatus(OrderStatus.ACTIVE);
        active.forEach(order -> add(ActiveOrder.from(order)));
        log.info("대기 중 주문 {}건을 메모리 인덱스에 올렸습니다.", active.size());
    }

    /**
     * 주기적 복구: DB에는 ACTIVE인데 메모리 인덱스에는 없는 주문을 다시 올린다.
     *
     * 언제 생기나? 체결 도중 DB 오류 등으로 예외가 나면, 주문은 이미 인덱스에서 빠졌지만 DB에서는 여전히 ACTIVE다.
     * 이대로면 서버를 재시작할 때까지 영원히 체결되지 않는다. 그래서 30초마다 DB와 대조해 되돌린다.
     *
     * 체결 "진행 중"인 주문(방금 인덱스에서 빠졌고 아직 커밋 전)이 함께 다시 올라올 수도 있다. 그래도 안전하다:
     * 같은 주문을 또 체결하려 해도 조건부 UPDATE(ACTIVE→EXECUTED)에서 한 번만 통과하기 때문이다(멱등성).
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    void reconcileWithDatabase() {
        int before = byId.size();
        orderRepository.findByStatus(OrderStatus.ACTIVE).forEach(order -> add(ActiveOrder.from(order)));
        int recovered = byId.size() - before;
        if (recovered > 0) {
            log.warn("DB에는 대기 중인데 인덱스에 없던 주문 {}건을 복구했습니다.", recovered);
        }
    }

    public void add(ActiveOrder order) {
        if (byId.putIfAbsent(order.id(), order) == null) {
            bySymbol.computeIfAbsent(order.symbol(), symbol -> new CopyOnWriteArrayList<>()).add(order);
        }
    }

    /** 주문 취소 시 호출. 이미 선점(claim)되어 처리 중인 주문이면 false. */
    public boolean remove(long orderId) {
        ActiveOrder order = byId.remove(orderId);
        if (order == null) {
            return false;
        }
        removeFromSymbolList(order);
        return true;
    }

    public List<ActiveOrder> findBySymbol(String symbol) {
        return bySymbol.getOrDefault(symbol, new CopyOnWriteArrayList<>());
    }

    /**
     * 주문을 "내가 처리하겠다"고 선점한다. ConcurrentHashMap.remove(key, value)는 원자적이라
     * 동시에 여러 스레드가 호출해도 true를 받는 스레드는 하나뿐이다.
     */
    public boolean claim(ActiveOrder order) {
        if (!byId.remove(order.id(), order)) {
            return false;
        }
        removeFromSymbolList(order);
        return true;
    }

    private void removeFromSymbolList(ActiveOrder order) {
        CopyOnWriteArrayList<ActiveOrder> list = bySymbol.get(order.symbol());
        if (list != null) {
            list.remove(order);
        }
    }
}
