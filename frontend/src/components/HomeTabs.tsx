"use client";

import { useState, type ReactNode } from "react";

const TABS = [
  { id: "watchlist", label: "시세" },
  { id: "holdings", label: "보유" },
  { id: "orders", label: "주문" },
  { id: "executions", label: "체결" },
] as const;

type TabId = (typeof TABS)[number]["id"];

/** 각 탭의 내용. 전부 서버 컴포넌트가 만든 결과를 그대로 받는다. 탭 id와 키가 1:1이라 하나라도 빠지면 컴파일 에러가 난다. */
type Props = { readonly panels: Readonly<Record<TabId, ReactNode>> };

/**
 * 홈 화면의 탭. 탭 전환(상태)만 클라이언트가 맡고, 각 탭의 내용은 서버에서 만들어 children처럼 받는다.
 * 이렇게 하면 탭 때문에 목록 코드까지 브라우저로 내려보낼 필요가 없다.
 */
export function HomeTabs({ panels }: Props) {
  const [active, setActive] = useState<TabId>("watchlist");

  return (
    <div>
      <div role="tablist" className="mb-4 flex gap-7 border-b border-white/5 px-2">
        {TABS.map((tab) => {
          const selected = tab.id === active;
          return (
            <button
              key={tab.id}
              type="button"
              role="tab"
              aria-selected={selected}
              onClick={() => setActive(tab.id)}
              className={`-mb-px border-b-2 pb-3 text-[17px] font-bold transition-colors ${
                selected ? "border-foreground text-foreground" : "border-transparent text-zinc-500 hover:text-zinc-300"
              }`}
            >
              {tab.label}
            </button>
          );
        })}
      </div>
      {/* 안 보이는 탭도 DOM에 남겨 두고 숨기기만 한다 → 탭을 오가도 목록이 다시 불러와지지 않는다 */}
      {TABS.map((tab) => (
        <div key={tab.id} role="tabpanel" hidden={active !== tab.id}>
          {panels[tab.id]}
        </div>
      ))}
    </div>
  );
}
