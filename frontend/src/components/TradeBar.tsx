"use client";

import { useCallback, useState } from "react";
import type { StockSummary } from "@/lib/api";
import { SIDE_LABEL, type OrderSide } from "@/types/order";
import { OrderDialog } from "./OrderDialog";

/** 화면 아래에 고정되는 매도/매수 버튼 (토스증권처럼 엄지로 누르기 쉬운 위치). 누르면 주문 팝업이 열린다. */
export function TradeBar({ stock }: { stock: StockSummary }) {
  const [side, setSide] = useState<OrderSide | null>(null);
  const close = useCallback(() => setSide(null), []);

  return (
    <>
      <div className="fixed inset-x-0 bottom-0 z-10 border-t border-white/5 bg-background/90 backdrop-blur">
        <div className="mx-auto flex w-full max-w-2xl gap-3 px-5 py-3">
          <button
            type="button"
            onClick={() => setSide("SELL")}
            className="h-14 flex-1 rounded-2xl bg-down text-lg font-bold text-white transition-opacity hover:opacity-90"
          >
            {SIDE_LABEL.SELL}
          </button>
          <button
            type="button"
            onClick={() => setSide("BUY")}
            className="h-14 flex-1 rounded-2xl bg-up text-lg font-bold text-white transition-opacity hover:opacity-90"
          >
            {SIDE_LABEL.BUY}
          </button>
        </div>
      </div>
      {/* side가 정해졌을 때만 마운트한다. 누른 버튼(매수/매도)이 팝업의 첫 선택값이 된다. */}
      {side && <OrderDialog stock={stock} initialSide={side} onClose={close} />}
    </>
  );
}
