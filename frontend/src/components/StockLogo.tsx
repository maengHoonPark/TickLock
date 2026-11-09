import { brandOf } from "@/lib/stockBrand";

type Props = {
  readonly symbol: string;
  readonly name: string;
  /** 로고 이미지 주소 (서버가 파일이 있는 것을 확인하고 내려준다). 없으면 null → 색상 + 약칭 아이콘 */
  readonly logoUrl: string | null;
};

/**
 * 종목 아이콘.
 *  - 로고 이미지가 있으면: 원형 배지 안에 로고를 비율 그대로(object-contain) 넣는다.
 *    가로로 긴 글자 로고(삼성·현대 등)도 잘리지 않도록 여백(padding)을 둔다.
 *  - 없으면: 기업 대표 색상 + 약칭 글자.
 * 이미지 존재 여부를 서버가 미리 알려주므로, 없는 파일을 브라우저가 요청해 404가 나는 일이 없다.
 */
export function StockLogo({ symbol, name, logoUrl }: Props) {
  const brand = brandOf(symbol, name);

  if (logoUrl) {
    return (
      <span
        className="flex size-11 shrink-0 items-center justify-center overflow-hidden rounded-full p-[5px] ring-1 ring-white/10"
        style={{ backgroundColor: brand.logoBackground }}
        aria-hidden
      >
        {/* eslint-disable-next-line @next/next/no-img-element -- 정적 파일을 그대로 보여주는 작은 아이콘이라 next/image가 필요 없다 */}
        <img src={logoUrl} alt="" className="size-full object-contain" />
      </span>
    );
  }

  return (
    <span
      className="flex size-11 shrink-0 items-center justify-center overflow-hidden rounded-full text-[15px] font-extrabold tracking-tight"
      style={{ backgroundColor: brand.background, color: brand.foreground }}
      aria-hidden
    >
      {brand.label}
    </span>
  );
}
