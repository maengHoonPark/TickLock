package com.ticklock.tick;

/**
 * 시세 체결 이벤트 1건.
 *
 * record를 쓰는 이유: 불변(immutable)이라 여러 스레드가 공유해도 안전하고,
 * getter/equals/toString을 직접 쓰지 않아도 되어 코드가 짧다.
 *
 * @param symbol 종목 코드 (예: 005930)
 * @param price  체결가 (원 단위 정수. 소수 오차를 피하려고 double을 쓰지 않는다)
 * @param volume 체결 수량
 * @param ts     발생 시각 (epoch millis). 수신까지 걸린 지연(lag)을 재는 기준이기도 하다
 * @param seq    발행 순번(1, 2, 3...). 수신 쪽에서 순서가 뒤바뀌어 도착했는지 검사하는 데 쓴다
 */
public record Tick(String symbol, long price, long volume, long ts, long seq) {
}
