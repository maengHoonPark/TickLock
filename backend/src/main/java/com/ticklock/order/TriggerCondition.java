package com.ticklock.order;

/** 발동 조건. LTE = 목표가 "이하"로 내려가면, GTE = 목표가 "이상"으로 오르면. */
public enum TriggerCondition {
    LTE,
    GTE;

    /** 현재가가 이 조건을 만족하는지. 체결 판단(P4)에서 쓴다. */
    public boolean isMet(long currentPrice, long targetPrice) {
        return switch (this) {
            case LTE -> currentPrice <= targetPrice;
            case GTE -> currentPrice >= targetPrice;
        };
    }
}
