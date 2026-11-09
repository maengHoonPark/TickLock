package com.ticklock.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** trigger_orders 테이블 매핑. "삼성전자가 70,000원 이하가 되면 10주 매수" 같은 조건 주문 1건. */
@Entity
@Table(name = "trigger_orders")
public class TriggerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private long userId;

    @Column(name = "wallet_id")
    private long walletId;

    private String symbol;

    @Enumerated(EnumType.STRING)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_condition")
    private TriggerCondition triggerCondition;

    @Column(name = "target_price")
    private long targetPrice;

    private long quantity;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    /** 클라이언트가 만든 멱등성 키. (user_id, request_key)가 UNIQUE라 같은 요청의 중복 등록을 DB가 막는다. */
    @Column(name = "request_key")
    private String requestKey;

    @Column(name = "created_at")
    private Instant createdAt;

    protected TriggerOrder() {
    }

    /** 새 주문은 항상 ACTIVE로 시작한다. 생성 규칙을 한 곳에 모아 두려고 생성자 대신 정적 팩토리를 쓴다. */
    public static TriggerOrder create(long userId, long walletId, String symbol, OrderSide side,
                                      TriggerCondition condition, long targetPrice, long quantity,
                                      String requestKey) {
        TriggerOrder order = new TriggerOrder();
        order.userId = userId;
        order.walletId = walletId;
        order.symbol = symbol;
        order.side = side;
        order.triggerCondition = condition;
        order.targetPrice = targetPrice;
        order.quantity = quantity;
        order.status = OrderStatus.ACTIVE;
        order.requestKey = requestKey;
        order.createdAt = Instant.now();
        return order;
    }

    /** 같은 멱등성 키로 "내용이 다른" 요청이 왔는지 구분하기 위한 비교. */
    public boolean hasSameContent(String symbol, OrderSide side, TriggerCondition condition,
                                  long targetPrice, long quantity) {
        return this.symbol.equals(symbol) && this.side == side && this.triggerCondition == condition
                && this.targetPrice == targetPrice && this.quantity == quantity;
    }

    public Long getId() {
        return id;
    }

    public long getWalletId() {
        return walletId;
    }

    public String getSymbol() {
        return symbol;
    }

    public OrderSide getSide() {
        return side;
    }

    public TriggerCondition getTriggerCondition() {
        return triggerCondition;
    }

    public long getTargetPrice() {
        return targetPrice;
    }

    public long getQuantity() {
        return quantity;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
