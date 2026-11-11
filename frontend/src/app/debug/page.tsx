import Link from "next/link";
import { DebugPanel } from "@/components/DebugPanel";
import { Header } from "@/components/Header";

/** 🛠 개발용 관찰 화면. DB와 시세 파이프라인의 현재 상태를 실시간으로 보여준다. (실제 서비스 화면이 아니다) */
export default function DebugPage() {
  return (
    <>
      <Header />
      <main className="mx-auto w-full max-w-2xl px-5 pb-20 pt-6">
        <Link href="/" className="mb-4 inline-block text-sm text-zinc-400 hover:text-zinc-200">
          ← 홈
        </Link>
        <div className="mb-6 flex items-center gap-2">
          <h1 className="text-[26px] font-bold leading-tight">관찰 화면</h1>
          <span className="rounded-full bg-amber-500/15 px-2.5 py-1 text-xs font-bold text-amber-400">개발용</span>
        </div>
        <DebugPanel />
      </main>
    </>
  );
}
