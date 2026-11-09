package com.ticklock.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * wallets 테이블 매핑. 유저당 1개이며 잔액(원 단위)을 가진다.
 * 잔액 차감(체결)은 P4에서 구현한다. 지금은 조회 전용이다.
 */
@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    private long balance;

    /**
     * 낙관적 락 버전. 누군가 먼저 수정했다면 저장 시 예외가 나서 "덮어쓰기(Lost Update)"를 막는다.
     * 분산 락이 뚫렸을 때를 대비한 두 번째 방어선이다.
     */
    @Version
    private long version;

    protected Wallet() {
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public long getBalance() {
        return balance;
    }
}
