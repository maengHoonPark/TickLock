import { existsSync } from "node:fs";
import path from "node:path";
import { z } from "zod";
import { ORDER_SIDES, ORDER_STATUSES, TRIGGER_CONDITIONS } from "@/types/order";
import { API_BASE_URL } from "./config";

const stockListSchema = z.array(
  z.object({
    symbol: z.string(),
    name: z.string(),
    price: z.number().int().positive(),
    basePrice: z.number().int().positive(), // 전일 종가 역할의 기준가 (등락 계산용)
  }),
);

const walletSchema = z.object({ balance: z.number().int().nonnegative() });

export const orderSchema = z.object({
  id: z.number().int(),
  symbol: z.string(),
  name: z.string(),
  side: z.enum(ORDER_SIDES),
  condition: z.enum(TRIGGER_CONDITIONS),
  targetPrice: z.number().int().positive(),
  quantity: z.number().int().positive(),
  status: z.enum(ORDER_STATUSES),
  createdAt: z.string(),
});

const holdingListSchema = z.array(
  z.object({
    symbol: z.string(),
    name: z.string(),
    quantity: z.number().int().positive(),
    averagePrice: z.number().int().nonnegative(), // 평균 매입가 (이동평균법)
  }),
);

const executionListSchema = z.array(
  z.object({
    id: z.number().int(),
    orderId: z.number().int(),
    symbol: z.string(),
    name: z.string(),
    side: z.enum(ORDER_SIDES),
    price: z.number().int().positive(),
    quantity: z.number().int().positive(),
    executedAt: z.string(),
  }),
);

export type Holding = z.infer<typeof holdingListSchema>[number];
export type Execution = z.infer<typeof executionListSchema>[number];

/** 백엔드가 주는 값 + 서버가 찾아낸 "로고 이미지 주소"(없으면 null) */
export type StockSummary = z.infer<typeof stockListSchema>[number] & { readonly logoUrl: string | null };
export type Wallet = z.infer<typeof walletSchema>;
export type Order = z.infer<typeof orderSchema>;

/**
 * 백엔드 GET 호출 공통 함수. 서버 컴포넌트에서 쓴다 (브라우저가 아니라 Next 서버가 백엔드를 호출).
 * 시세·잔액·주문은 계속 바뀌므로 캐시하지 않는다(no-store).
 * 응답은 zod로 런타임 검증해서, 백엔드가 계약을 어기면 여기서 바로 드러난다.
 */
async function getJson<T>(path: string, schema: z.ZodType<T>): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, { cache: "no-store" });
  if (!response.ok) {
    throw new Error(`${path} 조회 실패: HTTP ${response.status}`);
  }
  return schema.parse(await response.json());
}

/** 로고 파일 확장자. 앞에 있을수록 우선한다 (SVG는 크기를 키워도 선명하다). */
const LOGO_EXTENSIONS = ["svg", "png"] as const;

/**
 * public/logos/{종목코드}.svg 또는 .png 가 있는지 서버에서 미리 확인하고, 있으면 그 주소를 돌려준다.
 * 브라우저가 없는 파일을 요청해 404를 내거나 깨진 이미지가 보이는 일을 처음부터 없애기 위해서다.
 */
function findLogoUrl(symbol: string): string | null {
  const extension = LOGO_EXTENSIONS.find((ext) =>
    existsSync(path.join(process.cwd(), "public", "logos", `${symbol}.${ext}`)),
  );
  return extension ? `/logos/${symbol}.${extension}` : null;
}

/** 서버 컴포넌트에서만 호출한다 (파일 시스템을 읽는다). */
export async function fetchStocks(): Promise<StockSummary[]> {
  const stocks = await getJson("/api/stocks", stockListSchema);
  return stocks.map((stock) => ({ ...stock, logoUrl: findLogoUrl(stock.symbol) }));
}
export const fetchWallet = () => getJson("/api/wallet", walletSchema);
export const fetchHoldings = () => getJson("/api/holdings", holdingListSchema);
export const fetchExecutions = () => getJson("/api/executions", executionListSchema);
export const fetchOrders = () => getJson("/api/orders", z.array(orderSchema));
