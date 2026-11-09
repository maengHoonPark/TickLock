package com.ticklock.tick;

/** Redis Pub/Sub 채널 이름을 한 곳에서 관리해, 발행자와 구독자가 오타로 어긋나지 않게 한다. */
public final class TickChannel {

    public static final String NAME = "ticks";

    private TickChannel() {
    }
}
