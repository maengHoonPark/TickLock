import { fetchWallet } from "@/lib/api";
import { formatWon } from "@/lib/format";

/** 내 자산 카드. 서버 컴포넌트라서 잔액은 서버에서 가져와 완성된 HTML로 내려온다. */
export async function WalletCard() {
  const wallet = await fetchWallet().catch(() => null);

  return (
    <section className="relative mb-8 overflow-hidden rounded-3xl bg-gradient-to-br from-down/25 via-white/5 to-white/5 p-6">
      <p className="text-sm font-medium text-zinc-300">내 투자 · 주문 가능 금액</p>
      <p className="mt-2 text-[34px] font-extrabold leading-tight tabular-nums">
        {wallet ? formatWon(wallet.balance) : "불러오지 못했어요"}
      </p>
      <p className="mt-3 text-xs text-zinc-400">가상 투자금이에요. 실제 거래가 아니에요.</p>
    </section>
  );
}
