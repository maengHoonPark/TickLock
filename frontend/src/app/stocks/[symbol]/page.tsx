import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { Header } from "@/components/Header";
import { OrderList } from "@/components/OrderList";
import { StockLive } from "@/components/StockLive";
import { TradeBar } from "@/components/TradeBar";
import { fetchStocks } from "@/lib/api";

/**
 * 종목 상세 페이지.
 *
 * params(주소의 종목 코드)는 요청마다 달라지는 값이라, 이 페이지 최상단에서 await하면 페이지 전체가
 * "미리 만들어 둘 수 없는 화면"이 되어 버린다. 그래서 Promise 그대로 아래로 넘기고,
 * Suspense 안쪽 컴포넌트에서 await한다. (덕분에 헤더·뒤로가기는 즉시 보이고 종목 정보만 뒤따라 나타난다.)
 */
export default function StockPage({ params }: PageProps<"/stocks/[symbol]">) {
  return (
    <>
      <Header />
      <main className="mx-auto w-full max-w-2xl px-5 pb-32 pt-6">
        <Link href="/" className="mb-4 inline-block text-sm text-zinc-400 hover:text-zinc-200">
          ← 시세
        </Link>
        <Suspense fallback={<p className="text-zinc-500">불러오는 중…</p>}>
          <StockDetail params={params} />
        </Suspense>
      </main>
    </>
  );
}

async function StockDetail({ params }: { params: PageProps<"/stocks/[symbol]">["params"] }) {
  const { symbol } = await params;
  const stocks = await fetchStocks().catch(() => null);

  if (stocks === null) {
    return (
      <p className="rounded-2xl bg-white/5 p-5 text-zinc-400">
        백엔드에 연결할 수 없어요. <code>docker compose up -d</code>로 서버가 실행 중인지 확인해 주세요.
      </p>
    );
  }

  const stock = stocks.find((item) => item.symbol === symbol);
  if (!stock) notFound(); // 없는 종목 코드면 404 화면

  return (
    <>
      <StockLive stock={stock} />

      <section className="mt-10">
        <h2 className="mb-3 text-[20px] font-bold">이 종목의 조건 주문</h2>
        <Suspense fallback={<p className="text-zinc-500">불러오는 중…</p>}>
          <OrderList symbol={symbol} />
        </Suspense>
      </section>

      <TradeBar stock={stock} />
    </>
  );
}
