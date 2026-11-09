import Link from "next/link";
import { LiveBadge } from "./LiveBadge";

/** 상단 고정 헤더. 서버 컴포넌트이고, 상태가 필요한 LIVE 표시만 클라이언트 컴포넌트로 분리했다. */
export function Header() {
  return (
    <header className="sticky top-0 z-10 border-b border-white/5 bg-background/80 backdrop-blur">
      <div className="mx-auto flex h-14 w-full max-w-2xl items-center justify-between px-5">
        <Link href="/" className="flex items-center gap-2.5">
          <span className="flex size-7 items-center justify-center rounded-lg bg-down" aria-hidden>
            <svg viewBox="0 0 24 24" className="size-4 text-white" fill="none" stroke="currentColor" strokeWidth={3} strokeLinecap="round" strokeLinejoin="round">
              <path d="M5 13l4 4L19 7" />
            </svg>
          </span>
          <span className="text-lg font-bold tracking-tight">TickLock</span>
        </Link>
        <LiveBadge />
      </div>
    </header>
  );
}
