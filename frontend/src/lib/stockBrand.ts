/**
 * 종목 아이콘에 쓰는 표시 정보.
 *  - 로고 이미지가 있으면 그 이미지를 원형 배지 안에 보여주고(배경은 logoBackground),
 *  - 없으면 기업 대표 색상(background) + 약칭(label)으로 대신한다.
 * 로고 이미지는 public/logos/{종목코드}.svg|png 에 둔다. (로고의 상표권은 각 기업에 있다.)
 */
export type StockBrand = {
  /** 로고 이미지가 없을 때 아이콘 안에 보여줄 글자 (1~2자) */
  readonly label: string;
  /** 로고 이미지가 없을 때의 배경색 (CSS 색) */
  readonly background: string;
  /** 로고 이미지가 없을 때의 글자색 */
  readonly foreground: string;
  /** 로고 이미지 뒤에 깔 배경색. 대부분 흰색이고, 로고 색과 어울리는 경우만 다르게 둔다. */
  readonly logoBackground: string;
};

const WHITE = "#ffffff";

const BRANDS: Readonly<Record<string, StockBrand>> = {
  "005930": { label: "S", background: "#1428A0", foreground: "#ffffff", logoBackground: WHITE }, // 삼성전자
  "000660": { label: "SK", background: "#EA002C", foreground: "#ffffff", logoBackground: WHITE }, // SK하이닉스
  "035420": { label: "N", background: "#03C75A", foreground: "#ffffff", logoBackground: WHITE }, // NAVER
  "035720": { label: "K", background: "#FEE500", foreground: "#191919", logoBackground: "#FEE500" }, // 카카오: 노란 배경 위에 갈색 말풍선
  "005380": { label: "H", background: "#002C5F", foreground: "#ffffff", logoBackground: WHITE }, // 현대차
};

/** 등록되지 않은 종목은 이름 첫 글자 + 회색 배경으로 대체한다. */
export function brandOf(symbol: string, name: string): StockBrand {
  return (
    BRANDS[symbol] ?? { label: name.slice(0, 1), background: "#3f3f46", foreground: "#ffffff", logoBackground: WHITE }
  );
}
