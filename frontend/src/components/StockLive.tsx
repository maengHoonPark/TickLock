"use client";

import type { StockSummary } from "@/lib/api";
import { formatNumber } from "@/lib/format";
import { signed, signedPercent, toneOf, TONE_TEXT_CLASS } from "@/lib/tone";
import { usePriceStore } from "@/stores/priceStore";
import { AreaChart } from "./AreaChart";
import { RollingNumber } from "./RollingNumber";
import { StockLogo } from "./StockLogo";

/** 종목 상세 화면의 실시간 영역: 큰 가격, 등락, 차트, 고가/저가. 시세 스토어를 구독해 계속 갱신된다. */
export function StockLive({ stock }: { stock: StockSummary }) {
  const live = usePriceStore((state) => state.prices[stock.symbol]);
  const price = live?.price ?? stock.price;
  const history = live?.history ?? [];

  const change = price - stock.basePrice;
  const ratePercent = (change / stock.basePrice) * 100;
  const tone = toneOf(change);

  // 고가/저가는 "접속한 뒤 지금까지 받은 시세" 기준이다 (과거 데이터를 따로 저장해 두지 않았기 때문)
  const high = Math.max(price, ...history);
  const low = Math.min(price, ...history);

  return (
    <section>
      <div className="flex items-center gap-3">
        <StockLogo symbol={stock.symbol} name={stock.name} logoUrl={stock.logoUrl} />
        <div>
          <h1 className="text-xl font-bold">{stock.name}</h1>
          <p className="text-sm text-zinc-500">{stock.symbol}</p>
        </div>
      </div>

      <div className="mt-6">
        <div className="text-[44px] font-extrabold leading-tight">
          <RollingNumber value={price} />
          <span>원</span>
        </div>
        <p className={`mt-1 text-base font-semibold tabular-nums ${TONE_TEXT_CLASS[tone]}`}>
          어제보다 {signed(change)}원 ({signedPercent(ratePercent)})
        </p>
      </div>

      <div className="mt-6 -mx-1">
        <AreaChart points={history} baseline={stock.basePrice} colorClass={TONE_TEXT_CLASS[tone]} />
      </div>

      <dl className="mt-6 grid grid-cols-3 gap-3">
        <Stat label="기준가 (전일 종가)" value={stock.basePrice} />
        <Stat label="고가 (접속 후)" value={high} valueClass="text-up" />
        <Stat label="저가 (접속 후)" value={low} valueClass="text-down" />
      </dl>
    </section>
  );
}

function Stat({ label, value, valueClass = "" }: { label: string; value: number; valueClass?: string }) {
  return (
    <div className="rounded-2xl bg-white/5 p-4">
      <dt className="text-xs text-zinc-500">{label}</dt>
      <dd className={`mt-1 text-[17px] font-bold tabular-nums ${valueClass}`}>{formatNumber(value)}원</dd>
    </div>
  );
}
