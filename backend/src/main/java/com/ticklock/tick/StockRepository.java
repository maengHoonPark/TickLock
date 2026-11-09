package com.ticklock.tick;

import org.springframework.data.jpa.repository.JpaRepository;

/** 종목 조회용. 인터페이스만 선언하면 Spring Data JPA가 구현체를 자동으로 만들어 준다. */
public interface StockRepository extends JpaRepository<Stock, String> {
}
