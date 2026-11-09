import { useId } from "react";

const WIDTH = 600;
const HEIGHT = 220;
const PADDING_Y = 18;

type Props = {
  readonly points: readonly number[];
  /** 기준가(전일 종가). 점선으로 표시해서 지금 가격이 기준보다 위/아래인지 한눈에 보이게 한다. */
  readonly baseline: number;
  /** 선/면 색을 정하는 Tailwind 글자색 클래스 (text-up | text-down | text-zinc-500). currentColor로 쓴다. */
  readonly colorClass: string;
};

/**
 * 실시간 영역 차트. 차트 라이브러리 없이 SVG path로 직접 그린다.
 * 점이 늘어나면 오른쪽 끝에 "현재가 점"이 깜빡이며 따라간다.
 */
export function AreaChart({ points, baseline, colorClass }: Props) {
  const gradientId = useId();

  if (points.length < 2) {
    return (
      <div className="flex h-56 items-center justify-center text-sm text-zinc-500">
        실시간 시세를 모으는 중이에요…
      </div>
    );
  }

  // 기준가도 범위에 포함해서, 기준선이 차트 밖으로 밀려나지 않게 한다
  const min = Math.min(...points, baseline);
  const max = Math.max(...points, baseline);
  const range = max - min || 1;

  const yOf = (value: number) => PADDING_Y + (1 - (value - min) / range) * (HEIGHT - PADDING_Y * 2);
  const xOf = (index: number) => (index / (points.length - 1)) * WIDTH;

  const line = points.map((value, index) => `${index === 0 ? "M" : "L"}${xOf(index).toFixed(1)},${yOf(value).toFixed(1)}`).join(" ");
  const area = `${line} L${WIDTH},${HEIGHT} L0,${HEIGHT} Z`;

  const lastY = yOf(points[points.length - 1] ?? baseline);

  return (
    <div className={`relative ${colorClass}`}>
      <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} preserveAspectRatio="none" className="h-56 w-full" aria-hidden>
        <defs>
          {/* 선 아래를 위에서 아래로 옅어지는 색으로 채운다 */}
          <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="currentColor" stopOpacity={0.28} />
            <stop offset="100%" stopColor="currentColor" stopOpacity={0} />
          </linearGradient>
        </defs>
        <line
          x1={0}
          x2={WIDTH}
          y1={yOf(baseline)}
          y2={yOf(baseline)}
          stroke="#71717a"
          strokeWidth={1}
          strokeDasharray="4 4"
          vectorEffect="non-scaling-stroke"
        />
        <path d={area} fill={`url(#${gradientId})`} />
        <path
          d={line}
          fill="none"
          stroke="currentColor"
          strokeWidth={2}
          strokeLinejoin="round"
          strokeLinecap="round"
          vectorEffect="non-scaling-stroke" // 가로로 늘어나도 선 굵기가 일정하게 유지된다
        />
      </svg>
      {/* SVG는 가로로 늘어나(preserveAspectRatio none) 원이 찌그러지므로, 점은 HTML 요소로 겹쳐서 그린다 */}
      <span
        className="absolute right-0 size-3 -translate-y-1/2 translate-x-1/2"
        style={{ top: `${(lastY / HEIGHT) * 100}%` }}
        aria-hidden
      >
        <span className="absolute inline-flex size-full animate-ping rounded-full bg-current opacity-60" />
        <span className="relative inline-flex size-3 rounded-full bg-current" />
      </span>
    </div>
  );
}
