import type { StockSummary } from "@/lib/api";
import { StockRow } from "./StockRow";

/**
 * 종목 목록. 서버 컴포넌트라서 상태가 없고, 각 행(StockRow)이 자기 가격만 구독해서 실시간으로 갱신된다.
 * (실시간 연결 자체는 layout의 TickStreamConnector가 맡는다.)
 */
export function Dashboard({ stocks }: { stocks: StockSummary[] }) {
  return (
    <ul className="flex flex-col">
      {stocks.map((stock) => (
        <StockRow key={stock.symbol} stock={stock} />
      ))}
    </ul>
  );
}
