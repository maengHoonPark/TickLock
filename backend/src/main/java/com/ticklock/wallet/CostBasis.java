package com.ticklock.wallet;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 한 종목의 "평균 매입가" 계산기 (이동평균법).
 *
 * 규칙
 *  - 매수: 보유 수량과 총 매입 원가가 늘어난다. 평균 매입가 = 총 원가 ÷ 수량
 *  - 매도: 수량이 줄고, 원가도 "평균 매입가 × 판 수량"만큼 같은 비율로 줄어든다.
 *          → 팔아도 남은 주식의 평균 매입가는 변하지 않는다. (손익은 파는 시점에 실현되는 것)
 *
 * 왜 BigDecimal인가?
 *  원가 × 수량은 long 범위를 넘을 수 있고(1억 × 100만 × 100만), 나눗셈의 반올림 오차도 누적되기 때문이다.
 *  금액 자체는 long(원)으로 다루지만, 이런 "곱하고 나누는 중간 계산"만 BigDecimal로 안전하게 처리한다.
 */
public final class CostBasis {

    private long quantity;
    private BigDecimal totalCost = BigDecimal.ZERO;

    public void buy(long buyQuantity, long price) {
        quantity += buyQuantity;
        totalCost = totalCost.add(BigDecimal.valueOf(price).multiply(BigDecimal.valueOf(buyQuantity)));
    }

    public void sell(long sellQuantity) {
        if (quantity <= 0) {
            return; // 가진 게 없는데 판 기록은 정합성 문제지만, 여기서는 계산이 깨지지 않게만 한다
        }
        long actualQuantity = Math.min(sellQuantity, quantity);
        // 줄어드는 원가 = 평균 매입가 × 판 수량 (= 총 원가 × 판 수량 ÷ 보유 수량)
        BigDecimal removedCost = totalCost
                .multiply(BigDecimal.valueOf(actualQuantity))
                .divide(BigDecimal.valueOf(quantity), 10, RoundingMode.HALF_UP);
        totalCost = totalCost.subtract(removedCost);
        quantity -= actualQuantity;
        if (quantity == 0) {
            totalCost = BigDecimal.ZERO; // 전부 팔면 원가를 0으로 리셋 (다음 매수부터 새로 계산)
        }
    }

    public long quantity() {
        return quantity;
    }

    /** 평균 매입가(원). 보유 수량이 없으면 0. 원 단위로 반올림한다. */
    public long averagePrice() {
        if (quantity <= 0) {
            return 0;
        }
        return totalCost.divide(BigDecimal.valueOf(quantity), 0, RoundingMode.HALF_UP).longValueExact();
    }
}
