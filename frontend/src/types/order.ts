/**
 * 주문 관련 "닫힌 값 집합". enum 대신 `as const` 배열 + 유니온 타입을 쓴다.
 *  - 런타임에 추가 코드가 생기지 않고, 문자열이 서버(JSON/DB) 값과 1:1로 같다.
 *  - 배열 그대로 zod의 z.enum()에 넘길 수 있어서, "타입"과 "런타임 검증"이 한 곳에서 정의된다.
 */
export const ORDER_SIDES = ["BUY", "SELL"] as const;
export type OrderSide = (typeof ORDER_SIDES)[number];

export const TRIGGER_CONDITIONS = ["LTE", "GTE"] as const; // 이하 / 이상
export type TriggerCondition = (typeof TRIGGER_CONDITIONS)[number];

export const ORDER_STATUSES = ["ACTIVE", "EXECUTED", "FAILED", "CANCELED"] as const;
export type OrderStatus = (typeof ORDER_STATUSES)[number];

/** `satisfies Record<...>`: 값을 하나라도 빠뜨리면 컴파일 에러가 난다 (새 상태 추가 시 누락 방지). */
export const SIDE_LABEL = { BUY: "매수", SELL: "매도" } as const satisfies Record<OrderSide, string>;

export const CONDITION_LABEL = { LTE: "이하", GTE: "이상" } as const satisfies Record<TriggerCondition, string>;

/** 문장 속에서 쓰는 표현: "가격이 70,000원 {이하로 내려가면 | 이상으로 오르면} …" */
export const CONDITION_PHRASE = {
  LTE: "이하로 내려가면",
  GTE: "이상으로 오르면",
} as const satisfies Record<TriggerCondition, string>;

export const STATUS_LABEL = {
  ACTIVE: "대기 중",
  EXECUTED: "체결",
  FAILED: "실패",
  CANCELED: "취소",
} as const satisfies Record<OrderStatus, string>;

/**
 * 주문 등록 서버 액션의 결과. 판별 유니온이라 status만 확인하면 나머지 필드 타입이 좁혀진다.
 * (서버 액션 파일은 async 함수만 export할 수 있어서, 타입은 이 파일에 둔다.)
 */
export type PlaceOrderState =
  | { readonly status: "idle" }
  | { readonly status: "success"; readonly orderId: number }
  | { readonly status: "error"; readonly message: string };
