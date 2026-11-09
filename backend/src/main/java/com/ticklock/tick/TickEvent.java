package com.ticklock.tick;

/**
 * 브라우저로 내려보내는 메시지 형태. 프론트의 zod 스키마(types/messages.ts)와 1:1로 맞춰야 한다.
 * type 필드로 메시지 종류를 구분한다 (판별 유니온). 체결 알림 등은 나중에 다른 type으로 추가된다.
 */
public record TickEvent(String type, String symbol, long price, long ts) {

    public static TickEvent from(Tick tick) {
        return new TickEvent("tick", tick.symbol(), tick.price(), tick.ts());
    }
}
