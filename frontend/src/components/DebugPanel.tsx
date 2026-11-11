"use client";

import { useEffect, useState, type ReactNode } from "react";
import { fetchDebugSnapshot, type DebugSnapshot, type DebugSnapshotResult } from "@/lib/debugApi";
import { formatNumber, formatWon } from "@/lib/format";
import { CONDITION_LABEL, SIDE_LABEL, STATUS_LABEL } from "@/types/order";

const POLL_INTERVAL_MS = 2_000;

/** 테이블 이름 → 화면에 보여줄 설명 */
const TABLE_LABEL: Readonly<Record<string, string>> = {
  users: "유저",
  wallets: "지갑",
  trigger_orders: "조건 주문",
  executions: "체결",
  holdings: "보유 종목",
  price_ticks: "시세 로그 (최근 10분)",
};

const timeFormat = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hour12: false,
});

/** 처음 불러오기 전까지의 상태도 포함한 화면 상태 */
type PanelState = { readonly status: "loading" } | DebugSnapshotResult;

/**
 * 개발용 관찰 화면: DB와 시세 파이프라인의 현재 상태를 2초마다 새로 읽어 보여준다.
 * 값이 바뀐 칸은 잠깐 반짝여서, "주문을 넣는 순간 어디가 바뀌는지"가 눈에 보인다.
 */
export function DebugPanel() {
  const [state, setState] = useState<PanelState>({ status: "loading" });

  useEffect(() => {
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;

    // setInterval 대신 "끝난 뒤 다음 예약"으로 반복한다. 서버가 느려서 응답이 2초 넘게 걸려도 요청이 겹쳐 쌓이지 않는다.
    async function poll() {
      const result = await fetchDebugSnapshot();
      if (cancelled) return;
      setState(result);
      timer = setTimeout(poll, POLL_INTERVAL_MS);
    }
    void poll();

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, []);

  switch (state.status) {
    case "loading":
      return <p className="text-zinc-500">불러오는 중…</p>;
    case "disabled":
      return (
        <Notice>
          백엔드에서 개발용 API가 꺼져 있어요. <code>TICKLOCK_DEBUG_ENABLED=true</code>로 백엔드를 다시 시작해 주세요.
        </Notice>
      );
    case "error":
      return <Notice>{state.message}</Notice>;
    case "ok":
      return <SnapshotView snapshot={state.snapshot} />;
    default: {
      const unreachable: never = state;
      return unreachable;
    }
  }
}

function SnapshotView({ snapshot }: { snapshot: DebugSnapshot }) {
  const { wallet, pipeline } = snapshot;
  const backlog = pipeline.published - pipeline.received; // 발행됐지만 아직 처리 못 한 양

  return (
    <div className="flex flex-col gap-8">
      <p className="text-xs text-zinc-500">
        마지막 갱신 {timeFormat.format(new Date(snapshot.serverTime))} · 2초마다 자동 갱신 · 값이 바뀌면 반짝여요
      </p>

      <Section title="정합성 검사" hint="체결 기록으로 다시 계산한 잔액과 실제 잔액이 같아야 해요">
        <div
          className={`rounded-2xl p-5 ${wallet.consistent ? "bg-emerald-500/10 text-emerald-300" : "bg-up/15 text-up"}`}
        >
          <p className="text-lg font-bold">{wallet.consistent ? "✅ 정합성 OK" : "❌ 정합성 깨짐"}</p>
          <p className="mt-2 text-sm tabular-nums">
            실제 잔액 <b>{formatWon(wallet.balance)}</b> · 정답 잔액 <b>{formatWon(wallet.expectedBalance)}</b> · 차이{" "}
            <b>{formatWon(wallet.difference)}</b>
          </p>
        </div>
      </Section>

      <Section title="테이블별 행 수" hint="주문을 넣으면 trigger_orders, 체결되면 executions가 늘어요">
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          {Object.entries(snapshot.tableCounts).map(([table, count]) => (
            <div key={table} className="rounded-2xl bg-white/5 p-4">
              <p className="text-xs text-zinc-500">{TABLE_LABEL[table] ?? table}</p>
              <p className="text-[11px] text-zinc-600">{table}</p>
              <p className="mt-1 text-2xl font-bold tabular-nums">
                <Flash value={count} />
              </p>
            </div>
          ))}
        </div>
      </Section>

      <Section title="시세 파이프라인" hint="밀린 양이 계속 늘면 처리가 발행 속도를 못 따라가는 거예요">
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <Metric label="발행" value={pipeline.published} />
          <Metric label="처리 완료" value={pipeline.received} />
          <Metric label="밀린 양" value={backlog} />
          <Metric label="DB 저장" value={pipeline.stored} />
          <Metric label="버퍼 대기" value={pipeline.bufferSize} />
          <Metric label="유실(버퍼 초과)" value={pipeline.dropped} />
          <Metric label="유실(저장 실패)" value={pipeline.storeFailed} />
          <Metric label="순서 역전" value={pipeline.outOfOrder} />
        </div>
      </Section>

      <Section title="최근 조건 주문 (trigger_orders)">
        <DataTable
          headers={["id", "종목", "구분", "조건", "목표가", "수량", "상태"]}
          emptyText="아직 주문이 없어요"
          rows={snapshot.recentOrders.map((order) => ({
            key: order.id,
            cells: [
              order.id,
              order.symbol,
              SIDE_LABEL[order.side],
              CONDITION_LABEL[order.condition],
              formatNumber(order.targetPrice),
              order.quantity,
              STATUS_LABEL[order.status],
            ],
          }))}
        />
      </Section>

      <Section title="최근 체결 (executions)">
        <DataTable
          headers={["id", "주문id", "종목", "구분", "체결가", "수량", "시각"]}
          emptyText="아직 체결이 없어요"
          rows={snapshot.recentExecutions.map((execution) => ({
            key: execution.id,
            cells: [
              execution.id,
              execution.orderId,
              execution.symbol,
              SIDE_LABEL[execution.side],
              formatNumber(execution.price),
              execution.quantity,
              timeFormat.format(new Date(execution.executedAt)),
            ],
          }))}
        />
      </Section>

      <Section title="보유 종목 (holdings)">
        <DataTable
          headers={["종목", "이름", "수량", "평균 매입가"]}
          emptyText="보유 종목이 없어요"
          rows={snapshot.holdings.map((holding) => ({
            key: holding.symbol,
            cells: [holding.symbol, holding.name, holding.quantity, formatNumber(holding.averagePrice)],
          }))}
        />
      </Section>
    </div>
  );
}

/**
 * 값이 바뀔 때마다 반짝이는 숫자.
 * key={value}: 값이 바뀌면 이 요소를 새로 만들어 CSS 애니메이션을 처음부터 다시 재생한다. (상태·타이머가 필요 없다)
 */
function Flash({ value }: { value: number }) {
  return (
    <span key={value} className="value-flash rounded px-1">
      {formatNumber(value)}
    </span>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-2xl bg-white/5 p-4">
      <p className="text-xs text-zinc-500">{label}</p>
      <p className="mt-1 text-xl font-bold tabular-nums">
        <Flash value={value} />
      </p>
    </div>
  );
}

function Section({ title, hint, children }: { title: string; hint?: string; children: ReactNode }) {
  return (
    <section>
      <h2 className="text-lg font-bold">{title}</h2>
      {hint && <p className="mb-3 mt-0.5 text-xs text-zinc-500">{hint}</p>}
      {!hint && <div className="mb-3" />}
      {children}
    </section>
  );
}

type Row = { readonly key: string | number; readonly cells: readonly (string | number)[] };

function DataTable({ headers, rows, emptyText }: { headers: readonly string[]; rows: readonly Row[]; emptyText: string }) {
  if (rows.length === 0) {
    return <p className="rounded-2xl bg-white/5 p-4 text-sm text-zinc-500">{emptyText}</p>;
  }
  return (
    <div className="overflow-x-auto rounded-2xl bg-white/5">
      <table className="w-full text-left text-sm tabular-nums">
        <thead>
          <tr className="text-xs text-zinc-500">
            {headers.map((header) => (
              <th key={header} className="px-3 py-2 font-medium">
                {header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            // key={row.key}: 새 행이 생기면 그 행만 새로 만들어져 반짝인다 (기존 행은 그대로)
            <tr key={row.key} className="value-flash border-t border-white/5">
              {row.cells.map((cell, index) => (
                <td key={index} className="whitespace-nowrap px-3 py-2">
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Notice({ children }: { children: ReactNode }) {
  return <p className="rounded-2xl bg-white/5 p-5 text-zinc-400">{children}</p>;
}
