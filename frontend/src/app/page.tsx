import Link from "next/link";
import { Suspense } from "react";
import { Dashboard } from "@/components/Dashboard";
import { ExecutionList } from "@/components/ExecutionList";
import { Header } from "@/components/Header";
import { HoldingsTab } from "@/components/HoldingsTab";
import { HomeTabs } from "@/components/HomeTabs";
import { OrderList } from "@/components/OrderList";
import { WalletCard } from "@/components/WalletCard";
import { fetchStocks, type StockSummary } from "@/lib/api";

const LOADING = <p className="px-2 text-zinc-500">불러오는 중…</p>;

/**
 * 페이지 껍데기(헤더·탭 틀)는 즉시 렌더링하고, 백엔드 호출이 필요한 부분은 각각 Suspense로 감싼다.
 * 이 프로젝트는 cacheComponents가 켜져 있어서, 캐시되지 않는 데이터 접근은 반드시 Suspense 안에 있어야 한다.
 * 부분마다 따로 감싸면 느린 부분이 다른 부분을 막지 않고, 준비된 것부터 화면에 나타난다.
 */
export default function Home() {
  return (
    <>
      <Header />
      <main className="mx-auto w-full max-w-2xl px-3 pb-20 pt-8">
        <div className="px-2">
          <Suspense fallback={<div className="mb-8 h-[132px] animate-pulse rounded-3xl bg-white/5" />}>
            <WalletCard />
          </Suspense>
        </div>

        <HomeTabs
          panels={{
            watchlist: (
              <Suspense fallback={LOADING}>
                <StockBoard />
              </Suspense>
            ),
            holdings: (
              <Suspense fallback={LOADING}>
                <HoldingsTab />
              </Suspense>
            ),
            orders: (
              <Suspense fallback={LOADING}>
                <OrderList />
              </Suspense>
            ),
            executions: (
              <Suspense fallback={LOADING}>
                <ExecutionList />
              </Suspense>
            ),
          }}
        />
        <p className="mt-10 px-2 text-center text-xs leading-relaxed text-zinc-600">
          모든 시세는 가상으로 만든 값이에요. 종목 로고는 각 기업의 상표이며 Wikimedia Commons의 파일을 사용했어요.
          <br />
          <Link href="/debug" className="text-zinc-500 underline hover:text-zinc-300">
            개발용 관찰 화면 열기
          </Link>
        </p>
      </main>
    </>
  );
}

/** 서버 컴포넌트: 종목 목록을 서버에서 가져와 목록 컴포넌트에 넘긴다. */
async function StockBoard() {
  let stocks: StockSummary[];
  try {
    stocks = await fetchStocks();
  } catch {
    return (
      <p className="rounded-2xl bg-white/5 p-5 text-zinc-400">
        백엔드에 연결할 수 없어요. <code>docker compose up -d</code>로 서버가 실행 중인지 확인해 주세요.
      </p>
    );
  }
  return <Dashboard stocks={stocks} />;
}
