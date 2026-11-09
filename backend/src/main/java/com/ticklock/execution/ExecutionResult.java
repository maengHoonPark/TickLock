package com.ticklock.execution;

import com.ticklock.order.OrderSide;

/**
 * 체결 시도의 결과. sealed(봉인) 인터페이스라 "가능한 결과는 이 세 가지뿐"이 컴파일러에게 보장된다.
 * 결과를 예외나 null로 표현하지 않고 타입으로 표현하면, 호출하는 쪽이 모든 경우를 빠뜨리지 않고 처리하게 된다.
 */
public sealed interface ExecutionResult {

    /** 체결 성공 */
    record Executed(long orderId, String symbol, OrderSide side, long price, long quantity) implements ExecutionResult {
    }

    /** 조건은 맞았지만 체결하지 못함 (잔액/보유 수량 부족 등). 주문은 FAILED로 종료된다. */
    record Failed(long orderId, String symbol, String reason) implements ExecutionResult {
    }

    /** 이미 다른 경로(취소 등)에서 처리된 주문이라 아무것도 하지 않음. */
    record Skipped(long orderId) implements ExecutionResult {
    }
}
