package com.ticklock.execution;

import com.ticklock.order.OrderSide;

/**
 * 체결 결과를 브라우저에 알리는 메시지. 프론트 zod 스키마(types/messages.ts)와 1:1로 맞춘다.
 * type 필드로 종류를 구분하는 판별 유니온 형태다.
 */
public final class OrderEvents {

    private OrderEvents() {
    }

    public record Executed(String type, long orderId, String symbol, String name, OrderSide side,
                           long price, long quantity) {

        public static Executed of(ExecutionResult.Executed result, String name) {
            return new Executed("executed", result.orderId(), result.symbol(), name, result.side(),
                    result.price(), result.quantity());
        }
    }

    public record Failed(String type, long orderId, String symbol, String name, String reason) {

        public static Failed of(ExecutionResult.Failed result, String name) {
            return new Failed("failed", result.orderId(), result.symbol(), name, result.reason());
        }
    }
}
