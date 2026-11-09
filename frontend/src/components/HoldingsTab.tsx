import { fetchHoldings, fetchStocks } from "@/lib/api";
import { HoldingsPanel, type HoldingItem } from "./HoldingsPanel";

/**
 * 보유 종목 탭의 서버 쪽 부분: 보유 목록과 종목 정보를 가져와 하나로 합쳐서 클라이언트 컴포넌트에 넘긴다.
 * 두 요청은 서로 의존하지 않으므로 Promise.all로 동시에 보낸다 (순서대로 보내면 시간이 두 배가 된다).
 */
export async function HoldingsTab() {
  const result = await Promise.all([fetchHoldings(), fetchStocks()]).catch(() => null);

  if (result === null) {
    return <p className="px-2 text-zinc-500">보유 종목을 불러오지 못했어요.</p>;
  }
  const [holdings, stocks] = result;

  const stockBySymbol = new Map(stocks.map((stock) => [stock.symbol, stock]));
  const items = holdings.flatMap((holding): HoldingItem[] => {
    const stock = stockBySymbol.get(holding.symbol);
    return stock ? [{ stock, holding }] : []; // 종목 정보가 없는 보유분은 보여줄 수 없으니 건너뛴다
  });

  return <HoldingsPanel items={items} />;
}
