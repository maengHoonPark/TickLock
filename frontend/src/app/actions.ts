"use server";

import { revalidatePath } from "next/cache";
import { z } from "zod";
import { API_BASE_URL } from "@/lib/config";
import { ORDER_SIDES, TRIGGER_CONDITIONS, type PlaceOrderState } from "@/types/order";

/**
 * 서버 액션: 브라우저의 폼 제출을 Next 서버가 받아 백엔드 API를 대신 호출한다.
 * 장점: ① 별도 API 라우트 없이 폼과 서버 로직이 바로 연결되고 ② CORS를 신경 쓸 필요가 없으며
 *       ③ 끝난 뒤 revalidatePath로 화면의 서버 데이터(잔액·주문 목록)를 새로 가져오게 할 수 있다.
 */

const placeOrderSchema = z.object({
  symbol: z.string().min(1),
  side: z.enum(ORDER_SIDES),
  condition: z.enum(TRIGGER_CONDITIONS),
  // 폼 값은 항상 문자열이라 coerce로 숫자로 바꾼 뒤 정수/양수를 검증한다
  targetPrice: z.coerce.number().int().positive(),
  quantity: z.coerce.number().int().positive(),
  idempotencyKey: z.string().min(8).max(64),
});

const errorBodySchema = z.object({ message: z.string() });
const createdSchema = z.object({ id: z.number().int() });

const GENERIC_ERROR = "주문을 등록하지 못했어요. 잠시 후 다시 시도해 주세요.";

/** 백엔드가 {"message": "..."}로 보낸 오류 문구를 꺼낸다. 형식이 다르면 일반 문구로 대체. */
async function readErrorMessage(response: Response): Promise<string> {
  try {
    const parsed = errorBodySchema.safeParse(await response.json());
    return parsed.success ? parsed.data.message : GENERIC_ERROR;
  } catch {
    return GENERIC_ERROR;
  }
}

export async function placeOrderAction(_previous: PlaceOrderState, formData: FormData): Promise<PlaceOrderState> {
  const parsed = placeOrderSchema.safeParse(Object.fromEntries(formData));
  if (!parsed.success) {
    return { status: "error", message: "가격과 수량을 확인해 주세요." };
  }

  // idempotencyKey는 본문이 아니라 헤더로 보낸다. 같은 키로 재시도해도 주문은 한 건만 만들어진다.
  const { idempotencyKey, ...body } = parsed.data;

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}/api/orders`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "Idempotency-Key": idempotencyKey },
      body: JSON.stringify(body),
      cache: "no-store",
    });
  } catch {
    return { status: "error", message: "서버에 연결할 수 없어요." };
  }

  if (!response.ok) {
    return { status: "error", message: await readErrorMessage(response) };
  }

  const created = createdSchema.parse(await response.json());
  revalidatePath("/"); // 주문 목록을 다시 가져오게 한다
  return { status: "success", orderId: created.id };
}

/** 주문 취소. 이미 체결/취소된 주문이면 백엔드가 409를 주지만, 어느 쪽이든 최신 상태를 다시 보여주면 된다. */
export async function cancelOrderAction(orderId: number): Promise<void> {
  try {
    await fetch(`${API_BASE_URL}/api/orders/${orderId}`, { method: "DELETE", cache: "no-store" });
  } finally {
    revalidatePath("/");
  }
}
