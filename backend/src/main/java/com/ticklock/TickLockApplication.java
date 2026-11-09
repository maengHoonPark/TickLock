package com.ticklock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 애플리케이션 진입점. 기능별 패키지(tick, order, execution, wallet)는 이 패키지 아래에서 자동 스캔된다. */
@SpringBootApplication
@EnableScheduling // @Scheduled 메서드(시세 생성, 배치 전송)를 동작시키는 스위치
public class TickLockApplication {

    public static void main(String[] args) {
        SpringApplication.run(TickLockApplication.class, args);
    }
}
