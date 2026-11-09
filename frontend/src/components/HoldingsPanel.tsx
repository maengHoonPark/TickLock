"use client";

import Link from "next/link";
import type { Holding, StockSummary } from "@/lib/api";
import { formatNumber, formatWon } from "@/lib/format";
import { signed, signedPercent, toneOf, TONE_TEXT_CLASS } from "@/lib/tone";
import { usePriceStore } from "@/stores/priceStore";
import { RollingNumber } from "./RollingNumber";
import { StockLogo } from "./StockLogo";

/** 서버가 "보유 정보 + 종목 정보(로고 유무, 기준가 등)"를 합쳐서 내려준다. */
export type HoldingItem = {
  readonly stock: StockSummary;
  readonly holding: Holding;
};

/** 보유 종목 탭. 평가금액과 손익은 서버가 아니라 "지금 시세"로 이 화면이 계속 다시 계산한다. */
export function HoldingsPanel({ items }: { items: readonly HoldingItem[] }) {
  /**
   * 총 평가금액을 "숫자 하나"로 구독한다. Zustand는 선택한 값이 바뀔 때만 리렌더하므로,
   * 관련 없는 종목의 틱에는 요약 영역이 다시 그려지지 않는다.
   */
  const totalValue = usePriceStore((state) =>
    items.reduce((sum, { stock, holding }) => sum + (state.prices[stock.symbol]?.price ?? stock.price) * holding.quantity, 0),
  );

  if (items.length === 0) {
    return (
      <p className="rounded-2xl bg-white/5 p-5 text-sm text-zinc-500">
        보유한 종목이 아직 없어요. 종목 상세에서 매수 조건 주문을 등록해 보세요.
      </p>
    );
  }

  const totalCost = items.reduce((sum, { holding }) => sum + holding.averagePrice * holding.quantity, 0);
  const profit = totalValue - totalCost;
  const profitRate = totalCost > 0 ? (profit / totalCost) * 100 : 0;
  const tone = toneOf(profit);

  return (
    <div>
      <section className="mb-4 rounded-3xl bg-white/5 p-6">
        <p className="text-sm text-zinc-400">총 평가금액</p>
        <div className="mt-1 text-[32px] font-extrabold leading-tight">
          <RollingNumber value={totalValue} />
          <span>원</span>
        </div>
        <p className={`mt-1 text-[15px] font-semibold tabular-nums ${TONE_TEXT_CLASS[tone]}`}>
          {signed(profit)}원 ({signedPercent(profitRate)})
        </p>
        <p className="mt-3 text-xs text-zinc-500">매입금액 {formatWon(totalCost)}</p>
      </section>

      <ul className="flex flex-col">
        {items.map((item) => (
          <HoldingRow key={item.holding.symbol} item={item} />
        ))}
      </ul>
    </div>
  );
}

function HoldingRow({ item: { stock, holding } }: { item: HoldingItem }) {
  // 내 종목의 가격만 구독한다
  const price = usePriceStore((state) => state.prices[stock.symbol]?.price) ?? stock.price;

  const value = price * holding.quantity;
  const profit = (price - holding.averagePrice) * holding.quantity;
  const profitRate = holding.averagePrice > 0 ? ((price - holding.averagePrice) / holding.averagePrice) * 100 : 0;
  const tone = toneOf(profit);

  return (
    <li>
      <Link
        href={`/stocks/${stock.symbol}`}
        className="flex items-center gap-4 rounded-2xl px-3 py-4 transition-colors hover:bg-white/5"
      >
        <StockLogo symbol={stock.symbol} name={stock.name} logoUrl={stock.logoUrl} />
        <div className="min-w-0 flex-1">
          <p className="truncate text-[17px] font-semibold">{stock.name}</p>
          <p className="text-sm text-zinc-500">
            {formatNumber(holding.quantity)}주 · 평균 {formatWon(holding.averagePrice)}
          </p>
        </div>
        <div className="text-right">
          <p className="text-[17px] font-bold tabular-nums">{formatWon(value)}</p>
          <p className={`text-sm font-medium tabular-nums ${TONE_TEXT_CLASS[tone]}`}>
            {signed(profit)}원 ({signedPercent(profitRate)})
          </p>
        </div>
      </Link>
    </li>
  );
}
