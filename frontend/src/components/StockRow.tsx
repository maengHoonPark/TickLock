"use client";

import Link from "next/link";
import { usePriceStore } from "@/stores/priceStore";
import type { StockSummary } from "@/lib/api";
import { signed, signedPercent, toneOf, TONE_TEXT_CLASS, type Tone } from "@/lib/tone";
import { RollingNumber } from "./RollingNumber";
import { Sparkline } from "./Sparkline";
import { StockLogo } from "./StockLogo";

/** 틱 방향 → 가격 칸에 잠깐 깜빡이는 배경 애니메이션 (globals.css에 정의) */
const FLASH_CLASS = {
  up: "tick-flash-up",
  down: "tick-flash-down",
  flat: "",
} as const satisfies Record<Tone, string>;

/** 목록의 미니 차트는 최근 이만큼만 그린다 (스토어는 상세 차트용으로 더 많이 보관한다). */
const SPARKLINE_POINTS = 60;

export function StockRow({ stock }: { stock: StockSummary }) {
  // 내 종목의 가격만 구독한다 → 다른 종목 틱에는 이 행이 리렌더되지 않는다.
  const live = usePriceStore((state) => state.prices[stock.symbol]);
  const price = live?.price ?? stock.price;
  const history = live?.history ?? [stock.price];
  const tickDirection = live?.direction ?? "flat";

  const change = price - stock.basePrice;
  const ratePercent = (change / stock.basePrice) * 100;
  const tone = toneOf(change);

  return (
    <li>
      {/* 행 전체가 링크라서 클릭/터치/키보드(Tab+Enter) 모두로 종목 상세 화면으로 이동한다 */}
      <Link
        href={`/stocks/${stock.symbol}`}
        className="flex w-full items-center gap-4 rounded-2xl px-3 py-4 text-left transition-colors hover:bg-white/5"
      >
        <StockLogo symbol={stock.symbol} name={stock.name} logoUrl={stock.logoUrl} />

        <div className="min-w-0 flex-1">
          <p className="truncate text-[17px] font-semibold">{stock.name}</p>
          <p className="text-sm text-zinc-500">{stock.symbol}</p>
        </div>

        <Sparkline points={history.slice(-SPARKLINE_POINTS)} className={`h-8 w-20 shrink-0 ${TONE_TEXT_CLASS[tone]}`} />

        <div className="w-32 shrink-0 text-right">
          <div className="relative inline-block rounded-md px-1">
            {/*
              key={price}: 가격이 바뀔 때마다 이 "깜빡임 막"만 새로 만들어 애니메이션을 처음부터 다시 재생한다.
              숫자 컴포넌트는 그대로 두므로 롤링 전환은 끊기지 않는다.
            */}
            <span
              key={price}
              className={`pointer-events-none absolute inset-0 rounded-md ${FLASH_CLASS[tickDirection]}`}
            />
            <RollingNumber value={price} className="relative text-[17px] font-bold" />
            <span className="relative text-[17px] font-bold">원</span>
          </div>
          <p className={`text-sm font-medium tabular-nums ${TONE_TEXT_CLASS[tone]}`}>
            {signed(change)}원 ({signedPercent(ratePercent)})
          </p>
        </div>
      </Link>
    </li>
  );
}
