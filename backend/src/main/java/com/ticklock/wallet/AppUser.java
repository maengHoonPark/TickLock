package com.ticklock.wallet;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * users 테이블 매핑. 지금은 "데모 유저가 누구인지" 찾는 용도로만 쓴다.
 * 클래스 이름을 User가 아닌 AppUser로 한 이유: User는 보안/DB 쪽 예약어·다른 클래스와 헷갈리기 쉽다.
 */
@Entity
@Table(name = "users")
public class AppUser {

    @Id
    private Long id;

    private String email;

    protected AppUser() {
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }
}
