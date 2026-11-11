import { z } from "zod";
import { ORDER_SIDES, ORDER_STATUSES, TRIGGER_CONDITIONS } from "@/types/order";
import { API_BASE_URL } from "./config";

/** 개발용 관찰 API(/api/debug/snapshot)의 응답 모양. 백엔드 DebugController.Snapshot과 1:1로 맞춘다. */
const snapshotSchema = z.object({
  serverTime: z.string(),
  tableCounts: z.record(z.string(), z.number().int()),
  wallet: z.object({
    balance: z.number().int(),
    expectedBalance: z.number().int(),
    difference: z.number().int(),
    consistent: z.boolean(),
  }),
  holdings: z.array(
    z.object({
      symbol: z.string(),
      name: z.string(),
      quantity: z.number().int(),
      averagePrice: z.number().int(),
    }),
  ),
  recentOrders: z.array(
    z.object({
      id: z.number().int(),
      symbol: z.string(),
      side: z.enum(ORDER_SIDES),
      condition: z.enum(TRIGGER_CONDITIONS),
      targetPrice: z.number().int(),
      quantity: z.number().int(),
      status: z.enum(ORDER_STATUSES),
      createdAt: z.string(),
    }),
  ),
  recentExecutions: z.array(
    z.object({
      id: z.number().int(),
      orderId: z.number().int(),
      symbol: z.string(),
      side: z.enum(ORDER_SIDES),
      price: z.number().int(),
      quantity: z.number().int(),
      executedAt: z.string(),
    }),
  ),
  pipeline: z.object({
    published: z.number().int(),
    received: z.number().int(),
    stored: z.number().int(),
    dropped: z.number().int(),
    storeFailed: z.number().int(),
    outOfOrder: z.number().int(),
    bufferSize: z.number().int(),
  }),
});

export type DebugSnapshot = z.infer<typeof snapshotSchema>;

/** 결과를 판별 유니온으로 돌려줘서, 화면이 "성공/꺼져 있음/오류"를 빠뜨리지 않고 처리하게 한다. */
export type DebugSnapshotResult =
  | { readonly status: "ok"; readonly snapshot: DebugSnapshot }
  | { readonly status: "disabled" }
  | { readonly status: "error"; readonly message: string };

/** 브라우저에서 호출한다 (화면이 열려 있는 동안 주기적으로). */
export async function fetchDebugSnapshot(): Promise<DebugSnapshotResult> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/debug/snapshot`, { cache: "no-store" });
    if (response.status === 404) {
      return { status: "disabled" }; // 백엔드에서 개발용 API가 꺼져 있다
    }
    if (!response.ok) {
      return { status: "error", message: `서버 오류 (HTTP ${response.status})` };
    }
    return { status: "ok", snapshot: snapshotSchema.parse(await response.json()) };
  } catch {
    return { status: "error", message: "백엔드에 연결할 수 없어요." };
  }
}
