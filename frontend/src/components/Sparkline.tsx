const WIDTH = 100;
const HEIGHT = 32;
const PADDING = 2;

/**
 * 최근 가격 흐름을 보여주는 미니 차트.
 * 라이브러리 없이 SVG polyline 하나로 그린다. 색은 `currentColor`라서 부모의 text 색을 그대로 따른다.
 */
export function Sparkline({ points, className = "" }: { points: readonly number[]; className?: string }) {
  if (points.length < 2) {
    return <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} className={className} aria-hidden />;
  }

  const min = Math.min(...points);
  const max = Math.max(...points);
  const range = max - min || 1; // 모든 값이 같을 때 0으로 나누는 것을 방지

  const path = points
    .map((point, index) => {
      const x = (index / (points.length - 1)) * WIDTH;
      // SVG는 y가 아래로 커지므로 값이 클수록 위(작은 y)에 오도록 뒤집는다
      const y = HEIGHT - PADDING - ((point - min) / range) * (HEIGHT - PADDING * 2);
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(" ");

  return (
    <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} preserveAspectRatio="none" className={className} aria-hidden>
      <polyline
        points={path}
        fill="none"
        stroke="currentColor"
        strokeWidth={1.5}
        strokeLinejoin="round"
        strokeLinecap="round"
        vectorEffect="non-scaling-stroke" // 가로로 늘어나도 선 굵기가 일정하게 유지된다
      />
    </svg>
  );
}
