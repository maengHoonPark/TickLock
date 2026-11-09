import { create } from "zustand";

/** 차트용으로 보관하는 최근 가격 개수. 종목 상세의 큰 차트가 쓰고, 목록의 미니 차트는 이 중 뒤쪽 일부만 쓴다. */
const HISTORY_LIMIT = 240;

/** 한 종목의 가격 상태. direction은 직전 가격과 비교한 방향, history는 미니 차트용 최근 가격들. */
export type PriceState = {
  readonly price: number;
  readonly direction: "up" | "down" | "flat";
  readonly history: readonly number[];
};

type PriceStore = {
  readonly prices: Readonly<Record<string, PriceState>>;
  /** 실시간 스트림(SSE) 연결 여부. 헤더의 LIVE 표시에 쓴다. */
  readonly connected: boolean;
  readonly applyTick: (symbol: string, price: number) => void;
  readonly setConnected: (connected: boolean) => void;
};

/**
 * 실시간 시세 저장소.
 *
 * 컴포넌트는 `usePriceStore((s) => s.prices[symbol])`처럼 "자기 종목만" 구독한다.
 * Zustand는 선택한 값이 바뀔 때만 리렌더하므로, 초당 수백 건의 틱이 와도 해당 행만 다시 그려진다.
 */
export const usePriceStore = create<PriceStore>((set) => ({
  prices: {},
  connected: false,
  applyTick: (symbol, price) =>
    set((state) => {
      const previous = state.prices[symbol];
      const direction =
        previous === undefined || previous.price === price ? "flat" : price > previous.price ? "up" : "down";
      // 가격이 같으면 기록을 늘리지 않는다(차트에 평평한 구간이 길어지는 것을 방지)
      const history =
        previous === undefined
          ? [price]
          : previous.price === price
            ? previous.history
            : [...previous.history.slice(-(HISTORY_LIMIT - 1)), price];
      // 불변 업데이트: 기존 객체를 수정하지 않고 새 객체를 만든다 (변경 감지가 정확해진다)
      return { prices: { ...state.prices, [symbol]: { price, direction, history } } };
    }),
  setConnected: (connected) => set({ connected }),
}));
