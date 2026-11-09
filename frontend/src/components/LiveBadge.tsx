"use client";

import { usePriceStore } from "@/stores/priceStore";

/** 실시간 연결 상태 표시. 연결되면 초록 점이 깜빡이고, 끊기면 회색으로 "연결 중"을 보여준다. */
export function LiveBadge() {
  const connected = usePriceStore((state) => state.connected);

  return (
    <span className="flex items-center gap-2 rounded-full bg-white/5 px-3 py-1.5 text-xs font-semibold">
      <span className="relative flex size-2">
        {connected && <span className="absolute inline-flex size-full animate-ping rounded-full bg-emerald-400 opacity-75" />}
        <span className={`relative inline-flex size-2 rounded-full ${connected ? "bg-emerald-400" : "bg-zinc-500"}`} />
      </span>
      <span className={connected ? "text-emerald-400" : "text-zinc-500"}>{connected ? "LIVE" : "연결 중"}</span>
    </span>
  );
}
