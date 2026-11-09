const DIGITS = ["0", "1", "2", "3", "4", "5", "6", "7", "8", "9"] as const;
const LINE_HEIGHT_EM = 1.25;

const numberFormat = new Intl.NumberFormat("ko-KR");

/**
 * 숫자가 "롤링"하며 바뀌는 표시 컴포넌트 (토스증권의 숫자 전환 느낌).
 *
 * 원리: 각 자릿수마다 0~9가 세로로 쌓인 기둥을 만들고, 창(overflow hidden)으로 한 줄만 보이게 한 뒤
 * 기둥을 translateY로 움직인다. 값이 바뀌면 CSS transition이 알아서 부드럽게 굴려준다.
 * 자릿수의 key를 "오른쪽에서 몇 번째인지"로 잡아, 숫자가 늘어도 각 자리의 기둥이 그대로 재사용된다.
 */
export function RollingNumber({ value, className = "" }: { value: number; className?: string }) {
  const text = numberFormat.format(value);
  const chars = [...text];

  return (
    <span className={`inline-flex tabular-nums ${className}`} aria-label={text} role="text">
      {chars.map((char, index) => {
        const fromRight = chars.length - index;
        return /\d/.test(char) ? (
          <Digit key={fromRight} digit={Number(char)} />
        ) : (
          <span key={fromRight} aria-hidden>
            {char}
          </span>
        );
      })}
    </span>
  );
}

function Digit({ digit }: { digit: number }) {
  return (
    <span
      className="inline-block overflow-hidden"
      style={{ height: `${LINE_HEIGHT_EM}em`, lineHeight: `${LINE_HEIGHT_EM}em` }}
      aria-hidden
    >
      <span
        className="block transition-transform duration-300 ease-out"
        style={{ transform: `translateY(${-digit * LINE_HEIGHT_EM}em)` }}
      >
        {DIGITS.map((d) => (
          <span key={d} className="block" style={{ height: `${LINE_HEIGHT_EM}em` }}>
            {d}
          </span>
        ))}
      </span>
    </span>
  );
}
