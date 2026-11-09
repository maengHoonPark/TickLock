import { formatNumber } from "./format";

/**
 * 등락 방향. 한국 증시 관례: 상승=빨강, 하락=파랑, 보합=회색.
 * 시세 목록·종목 상세·보유 종목이 모두 같은 규칙으로 색을 칠하도록 한 곳에 모았다.
 */
export type Tone = "up" | "down" | "flat";

/** `satisfies Record<Tone, string>`: 방향을 추가하고 색을 빠뜨리면 컴파일 에러로 알려준다. */
export const TONE_TEXT_CLASS = {
  up: "text-up",
  down: "text-down",
  flat: "text-zinc-500",
} as const satisfies Record<Tone, string>;

export function toneOf(change: number): Tone {
  return change > 0 ? "up" : change < 0 ? "down" : "flat";
}

/** 양수에는 + 기호를 붙여 표시한다. 12345 → "+12,345", -300 → "-300" */
export function signed(value: number): string {
  return `${value > 0 ? "+" : ""}${formatNumber(value)}`;
}

/** 등락률 표시. 0.1234 → "+0.12%" */
export function signedPercent(percent: number): string {
  return `${percent > 0 ? "+" : ""}${percent.toFixed(2)}%`;
}
