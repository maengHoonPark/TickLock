import { cancelOrderAction } from "@/app/actions";
import { fetchOrders, type Order } from "@/lib/api";
import { formatWon } from "@/lib/format";
import { CONDITION_LABEL, SIDE_LABEL, STATUS_LABEL, type OrderStatus } from "@/types/order";

/** 상태별 배지 색. Record로 선언해서 새 상태가 생기면 여기서 컴파일 에러로 알려준다. */
const STATUS_BADGE_CLASS = {
  ACTIVE: "bg-down/15 text-down",
  EXECUTED: "bg-emerald-500/15 text-emerald-400",
  FAILED: "bg-up/15 text-up",
  CANCELED: "bg-white/10 text-zinc-400",
} as const satisfies Record<OrderStatus, string>;

/**
 * 내 조건 주문 목록 (서버 컴포넌트). 취소 버튼은 서버 액션과 연결된 폼이라 클라이언트 JS 없이도 동작한다.
 * symbol을 주면 그 종목의 주문만 보여준다 (종목 상세 화면용).
 */
export async function OrderList({ symbol }: { symbol?: string }) {
  const allOrders = await fetchOrders().catch(() => null);

  if (allOrders === null) {
    return <p className="px-2 text-zinc-500">주문 목록을 불러오지 못했어요.</p>;
  }
  const orders = symbol ? allOrders.filter((order) => order.symbol === symbol) : allOrders;

  if (orders.length === 0) {
    return (
      <p className="rounded-2xl bg-white/5 p-5 text-sm text-zinc-500">
        아직 등록한 조건 주문이 없어요. 종목을 눌러 상세 화면에서 매수/매도를 눌러 보세요.
      </p>
    );
  }

  return (
    <ul className="flex flex-col">
      {orders.map((order) => (
        <OrderRow key={order.id} order={order} />
      ))}
    </ul>
  );
}

function OrderRow({ order }: { order: Order }) {
  return (
    <li className="flex items-center gap-3 rounded-2xl px-3 py-4">
      <div className="min-w-0 flex-1">
        <p className="truncate text-[17px] font-semibold">{order.name}</p>
        <p className="text-sm text-zinc-500">
          {formatWon(order.targetPrice)} {CONDITION_LABEL[order.condition]} · {SIDE_LABEL[order.side]}{" "}
          {order.quantity}주
        </p>
      </div>
      <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${STATUS_BADGE_CLASS[order.status]}`}>
        {STATUS_LABEL[order.status]}
      </span>
      {order.status === "ACTIVE" && (
        // bind(null, id): 주문 번호를 미리 넣어둔 서버 액션. 폼 제출 시 이 주문만 취소된다.
        <form action={cancelOrderAction.bind(null, order.id)}>
          <button type="submit" className="rounded-lg bg-white/10 px-3 py-1.5 text-xs font-medium hover:bg-white/15">
            취소
          </button>
        </form>
      )}
    </li>
  );
}
