package com.ticklock.order;

/** 매수/매도. enum은 "허용되는 값의 닫힌 집합"이라 잘못된 문자열이 들어오면 역직렬화 단계에서 막힌다. */
public enum OrderSide {
    BUY,
    SELL
}
