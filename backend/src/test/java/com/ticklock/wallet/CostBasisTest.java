package com.ticklock.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 평균 매입가 계산 단위 테스트. DB/스프링 없이 순수 계산만 검증하므로 몇 밀리초 만에 끝난다. */
class CostBasisTest {

    @Test
    @DisplayName("같은 가격으로 사면 평균 매입가는 그 가격이다")
    void samePriceKeepsAverage() {
        CostBasis basis = new CostBasis();
        basis.buy(10, 70_000);
        basis.buy(5, 70_000);

        assertThat(basis.quantity()).isEqualTo(15);
        assertThat(basis.averagePrice()).isEqualTo(70_000);
    }

    @Test
    @DisplayName("다른 가격으로 나눠 사면 수량으로 가중평균한다")
    void weightedAverage() {
        CostBasis basis = new CostBasis();
        basis.buy(10, 70_000); // 70만원
        basis.buy(30, 74_000); // 222만원  → 합계 292만원 / 40주 = 73,000원

        assertThat(basis.averagePrice()).isEqualTo(73_000);
    }

    @Test
    @DisplayName("일부를 팔아도 남은 주식의 평균 매입가는 변하지 않는다")
    void partialSellKeepsAverage() {
        CostBasis basis = new CostBasis();
        basis.buy(10, 70_000);
        basis.buy(30, 74_000); // 평균 73,000원
        basis.sell(15);

        assertThat(basis.quantity()).isEqualTo(25);
        assertThat(basis.averagePrice()).isEqualTo(73_000);
    }

    @Test
    @DisplayName("전부 팔고 다시 사면 이전 원가가 섞이지 않고 새로 계산한다")
    void resetsAfterSellingEverything() {
        CostBasis basis = new CostBasis();
        basis.buy(10, 70_000);
        basis.sell(10);
        basis.buy(5, 80_000);

        assertThat(basis.quantity()).isEqualTo(5);
        assertThat(basis.averagePrice()).isEqualTo(80_000);
    }

    @Test
    @DisplayName("long 범위를 넘는 큰 값도 오버플로 없이 계산한다")
    void handlesHugeNumbers() {
        CostBasis basis = new CostBasis();
        basis.buy(1_000_000, 100_000_000); // 원가 100조 원
        basis.sell(1);

        assertThat(basis.quantity()).isEqualTo(999_999);
        assertThat(basis.averagePrice()).isEqualTo(100_000_000);
    }

    @Test
    @DisplayName("보유량보다 많이 팔아도 계산이 깨지지 않는다")
    void sellMoreThanHeldDoesNotBreak() {
        CostBasis basis = new CostBasis();
        basis.buy(3, 50_000);
        basis.sell(10);

        assertThat(basis.quantity()).isZero();
        assertThat(basis.averagePrice()).isZero();
    }
}
