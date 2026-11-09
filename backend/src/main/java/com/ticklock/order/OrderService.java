package com.ticklock.order;

import com.ticklock.common.ApiException;
import com.ticklock.tick.StockCatalog;
import com.ticklock.wallet.DemoAccount;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 조건 주문 등록/취소/조회. */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** 주문 등록에 필요한 값들을 한 덩어리로. 파라미터가 5개 넘게 늘어나는 것을 막고 의미를 이름으로 드러낸다. */
    public record CreateCommand(String symbol, OrderSide side, TriggerCondition condition,
                                long targetPrice, long quantity, String requestKey) {
    }

    private final OrderRepository orderRepository;
    private final StockCatalog stockCatalog;
    private final DemoAccount demoAccount;
    private final ActiveOrderIndex activeOrderIndex;

    public OrderService(OrderRepository orderRepository, StockCatalog stockCatalog,
                        DemoAccount demoAccount, ActiveOrderIndex activeOrderIndex) {
        this.orderRepository = orderRepository;
        this.stockCatalog = stockCatalog;
        this.demoAccount = demoAccount;
        this.activeOrderIndex = activeOrderIndex;
    }

    /**
     * 주문 등록 (멱등).
     *
     * 같은 requestKey로 여러 번 호출해도 주문은 한 건만 만들어지고, 항상 같은 주문을 돌려준다.
     * → 네트워크가 끊겨 클라이언트가 재시도하거나 버튼을 두 번 눌러도 중복 주문이 생기지 않는다.
     *
     * 이 메서드에 @Transactional을 붙이지 않은 것이 의도다.
     *  중복 키로 INSERT가 실패하면 그 트랜잭션은 "롤백 전용"이 되어 같은 트랜잭션 안에서는 재조회를 못 한다.
     *  그래서 저장(save)은 자체 트랜잭션으로 끝내고, 실패하면 트랜잭션 밖에서 다시 조회한다.
     */
    public TriggerOrder create(CreateCommand command) {
        if (!stockCatalog.exists(command.symbol())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "존재하지 않는 종목이에요.");
        }
        DemoAccount.Identity me = demoAccount.identity();

        return orderRepository.findByUserIdAndRequestKey(me.userId(), command.requestKey())
                .map(existing -> ensureSameContent(existing, command))
                .orElseGet(() -> insertOrFindExisting(me, command));
    }

    private TriggerOrder insertOrFindExisting(DemoAccount.Identity me, CreateCommand command) {
        TriggerOrder order = TriggerOrder.create(me.userId(), me.walletId(), command.symbol(),
                command.side(), command.condition(), command.targetPrice(), command.quantity(),
                command.requestKey());
        try {
            TriggerOrder saved = orderRepository.save(order);
            // DB에 확정 저장된 "다음에" 메모리 인덱스에 올린다. 순서가 반대면 저장 실패한 주문이 체결될 수 있다.
            activeOrderIndex.add(ActiveOrder.from(saved));
            return saved;
        } catch (DataIntegrityViolationException e) {
            // 거의 동시에 같은 키의 요청 2건이 들어와 둘 다 "없음"을 본 경우. UNIQUE 제약이 한 건만 통과시킨다.
            log.info("동시에 들어온 중복 주문 요청을 감지해 기존 주문을 돌려줍니다. key={}", command.requestKey());
            TriggerOrder existing = orderRepository
                    .findByUserIdAndRequestKey(me.userId(), command.requestKey())
                    .orElseThrow(() -> e); // 중복 키가 원인이 아니었다면(다른 제약 위반) 원래 예외를 그대로 던진다
            return ensureSameContent(existing, command);
        }
    }

    /** 같은 키인데 내용이 다르면 클라이언트 버그이므로 조용히 기존 주문을 돌려주지 않고 거절한다. */
    private TriggerOrder ensureSameContent(TriggerOrder existing, CreateCommand command) {
        if (!existing.hasSameContent(command.symbol(), command.side(), command.condition(),
                command.targetPrice(), command.quantity())) {
            throw new ApiException(HttpStatus.CONFLICT, "같은 요청 키로 다른 내용의 주문이 이미 있어요.");
        }
        return existing;
    }

    /** 주문 취소. ACTIVE인 주문만 취소할 수 있다. */
    public void cancel(long orderId) {
        long userId = demoAccount.identity().userId();
        int changed = orderRepository.changeStatus(orderId, userId, OrderStatus.ACTIVE, OrderStatus.CANCELED);
        if (changed == 1) {
            activeOrderIndex.remove(orderId);
            return;
        }
        // 0행이면 "내 주문이 아님/없음"과 "이미 처리된 주문"을 구분해서 알려준다
        throw orderRepository.existsById(orderId)
                ? new ApiException(HttpStatus.CONFLICT, "이미 체결되었거나 취소된 주문이에요.")
                : new ApiException(HttpStatus.NOT_FOUND, "주문을 찾을 수 없어요.");
    }

    public List<TriggerOrder> list() {
        return orderRepository.findByUserIdOrderByIdDesc(demoAccount.identity().userId());
    }
}
