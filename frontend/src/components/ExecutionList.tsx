import { fetchExecutions, type Execution } from "@/lib/api";
import { formatWon } from "@/lib/format";
import { SIDE_LABEL, type OrderSide } from "@/types/order";

/** 매수=빨강, 매도=파랑 (토스 관례). Record로 선언해 값이 늘어나면 컴파일 에러로 알려준다. */
const SIDE_BADGE_CLASS = {
  BUY: "bg-up/15 text-up",
  SELL: "bg-down/15 text-down",
} as const satisfies Record<OrderSide, string>;

/** 체결 시각 표시용. 서버 시간대가 달라도 한국 시간으로 보이게 시간대를 명시한다. */
const timeFormat = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul",
  month: "numeric",
  day: "numeric",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hour12: false,
});

/** 체결 내역 (서버 컴포넌트, 최신순). */
export async function ExecutionList() {
  const executions = await fetchExecutions().catch(() => null);

  if (executions === null) {
    return <p className="px-2 text-zinc-500">체결 내역을 불러오지 못했어요.</p>;
  }
  if (executions.length === 0) {
    return (
      <p className="rounded-2xl bg-white/5 p-5 text-sm text-zinc-500">
        아직 체결된 거래가 없어요. 조건 주문이 체결되면 여기에 쌓여요.
      </p>
    );
  }

  return (
    <ul className="flex flex-col">
      {executions.map((execution) => (
        <ExecutionRow key={execution.id} execution={execution} />
      ))}
    </ul>
  );
}

function ExecutionRow({ execution }: { execution: Execution }) {
  const amount = execution.price * execution.quantity;

  return (
    <li className="flex items-center gap-3 rounded-2xl px-3 py-4">
      <span className={`rounded-full px-2.5 py-1 text-xs font-bold ${SIDE_BADGE_CLASS[execution.side]}`}>
        {SIDE_LABEL[execution.side]}
      </span>
      <div className="min-w-0 flex-1">
        <p className="truncate text-[17px] font-semibold">{execution.name}</p>
        <p className="text-sm text-zinc-500">{timeFormat.format(new Date(execution.executedAt))}</p>
      </div>
      <div className="text-right">
        <p className="text-[17px] font-bold tabular-nums">{formatWon(amount)}</p>
        <p className="text-sm text-zinc-500 tabular-nums">
          {formatWon(execution.price)} · {execution.quantity}주
        </p>
      </div>
    </li>
  );
}
