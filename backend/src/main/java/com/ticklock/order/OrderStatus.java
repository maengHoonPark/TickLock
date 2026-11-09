package com.ticklock.order;

/**
 * 주문 상태. 한 방향으로만 이동한다.
 * ACTIVE → EXECUTED(체결) / FAILED(잔액 부족 등) / CANCELED(사용자 취소). 종료 상태에서는 되돌아가지 않는다.
 */
public enum OrderStatus {
    ACTIVE,
    EXECUTED,
    FAILED,
    CANCELED
}
