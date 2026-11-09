package com.ticklock.order;

/**
 * 메모리 인덱스에 올려두는 "대기 중 주문"의 가벼운 사본.
 * 시세가 들어올 때마다 DB를 조회할 수 없으므로, 판단에 필요한 값만 메모리에 들고 있는다.
 */
public record ActiveOrder(long id, long walletId, String symbol, OrderSide side,
                          TriggerCondition condition, long targetPrice, long quantity) {

    public static ActiveOrder from(TriggerOrder order) {
        return new ActiveOrder(order.getId(), order.getWalletId(), order.getSymbol(), order.getSide(),
                order.getTriggerCondition(), order.getTargetPrice(), order.getQuantity());
    }

    public boolean isTriggeredBy(long currentPrice) {
        return condition.isMet(currentPrice, targetPrice);
    }
}
