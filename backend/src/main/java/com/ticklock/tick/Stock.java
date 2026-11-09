package com.ticklock.tick;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** stocks 테이블과 매핑되는 종목 마스터. 읽기 전용으로만 쓴다. */
@Entity
@Table(name = "stocks")
public class Stock {

    @Id
    private String symbol;

    private String name;

    @Column(name = "base_price")
    private long basePrice;

    /** JPA가 리플렉션으로 객체를 만들 때 필요한 기본 생성자. 직접 호출하지 않는다. */
    protected Stock() {
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public long getBasePrice() {
        return basePrice;
    }
}
