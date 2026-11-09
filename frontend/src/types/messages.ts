import { z } from "zod";
import { ORDER_SIDES } from "./order";

/**
 * 서버 → 브라우저로 오는 실시간 메시지의 "계약(contract)".
 *
 * 왜 zod를 쓰나?
 *  TypeScript 타입은 컴파일 때만 존재해서, 서버가 실제로 보낸 JSON이 맞는 모양인지는 보장하지 못한다.
 *  zod 스키마로 런타임에 검증하고, 같은 스키마에서 타입을 뽑아(z.infer) 타입과 검증이 어긋나지 않게 한다.
 */

// 판별 유니온(discriminated union): `type` 필드 값에 따라 나머지 필드의 모양이 달라진다.
export const serverMessageSchema = z.discriminatedUnion("type", [
  z.object({
    type: z.literal("tick"),
    symbol: z.string(),
    price: z.number().int().positive(), // 원 단위 정수
    ts: z.number().int(),
  }),
  // 조건이 맞아 체결됨
  z.object({
    type: z.literal("executed"),
    orderId: z.number().int(),
    symbol: z.string(),
    name: z.string(),
    side: z.enum(ORDER_SIDES),
    price: z.number().int().positive(),
    quantity: z.number().int().positive(),
  }),
  // 조건은 맞았지만 잔액/보유 수량 부족 등으로 체결 실패
  z.object({
    type: z.literal("failed"),
    orderId: z.number().int(),
    symbol: z.string(),
    name: z.string(),
    reason: z.string(),
  }),
]);

export type ServerMessage = z.infer<typeof serverMessageSchema>;

/**
 * 받은 문자열을 안전하게 ServerMessage로 변환한다.
 * 형식이 틀리면 예외를 던지는 대신 null을 돌려줘서, 호출 쪽이 "무시"를 선택할 수 있게 한다.
 */
export function parseServerMessage(raw: string): ServerMessage | null {
  try {
    const result = serverMessageSchema.safeParse(JSON.parse(raw));
    return result.success ? result.data : null;
  } catch {
    return null; // JSON 문법 자체가 깨진 경우
  }
}
