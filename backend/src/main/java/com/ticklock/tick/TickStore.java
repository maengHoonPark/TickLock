package com.ticklock.tick;

/**
 * 시세를 DB에 남기는 전략.
 *  - direct : 틱마다 즉시 INSERT (비교 기준 "나쁜 예")
 *  - batch  : 메모리에 모았다가 주기적으로 한꺼번에 INSERT (기본값)
 * 같은 부하에서 두 방식의 처리량·지연을 비교하려고 설정(ticklock.tick.storage.mode)으로 바꿔 끼울 수 있게 했다.
 */
public interface TickStore {

    void save(Tick tick);
}
