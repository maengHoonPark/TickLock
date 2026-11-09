"use client";

import { useActionState, useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { placeOrderAction } from "@/app/actions";
import type { StockSummary } from "@/lib/api";
import { formatNumber, formatWon } from "@/lib/format";
import { usePriceStore } from "@/stores/priceStore";
import { StockLogo } from "./StockLogo";
import {
  CONDITION_LABEL,
  CONDITION_PHRASE,
  ORDER_SIDES,
  SIDE_LABEL,
  TRIGGER_CONDITIONS,
  type OrderSide,
  type PlaceOrderState,
  type TriggerCondition,
} from "@/types/order";

const IDLE_STATE: PlaceOrderState = { status: "idle" };

/** 숫자 입력칸용: 숫자 이외의 글자를 모두 지운다. ("70,000원" 같은 붙여넣기도 안전하게 처리) */
const digitsOnly = (value: string): string => value.replace(/\D/g, "");

/** 입력 문자열("70000") → 화면 표시("70,000"). 비어 있으면 빈 칸. */
const display = (digits: string): string => (digits === "" ? "" : formatNumber(Number(digits)));

type Props = {
  readonly stock: StockSummary;
  /** 팝업이 열릴 때 처음 선택되어 있을 매수/매도. 종목 상세의 어느 버튼을 눌렀는지에 따라 달라진다. */
  readonly initialSide?: OrderSide;
  readonly onClose: () => void;
};

/**
 * 조건 주문 등록 팝업.
 * 브라우저 기본 <dialog>를 쓴다: 포커스 가두기, ESC로 닫기, 뒤 화면 비활성화를 브라우저가 처리해 준다.
 */
export function OrderDialog({ stock, initialSide = "BUY", onClose }: Props) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const livePrice = usePriceStore((state) => state.prices[stock.symbol]?.price) ?? stock.price;

  const [side, setSide] = useState<OrderSide>(initialSide);
  const [condition, setCondition] = useState<TriggerCondition>("LTE");
  const [targetPrice, setTargetPrice] = useState(String(livePrice));
  const [quantity, setQuantity] = useState("1");

  /**
   * 멱등성 키: 팝업을 열 때 딱 한 번 만든다.
   * 버튼을 두 번 누르거나 네트워크 재시도가 일어나도 같은 키가 가므로, 서버는 주문을 한 건만 만든다.
   */
  const [idempotencyKey] = useState(() => crypto.randomUUID());

  const [state, formAction, pending] = useActionState(placeOrderAction, IDLE_STATE);

  useEffect(() => {
    dialogRef.current?.showModal();
  }, []);

  useEffect(() => {
    if (state.status === "success") {
      toast.success("조건 주문을 등록했어요", {
        description: `${stock.name} ${formatWon(Number(targetPrice))} ${CONDITION_LABEL[condition]} · ${SIDE_LABEL[side]} ${quantity}주`,
      });
      onClose(); // 팝업이 닫히며 언마운트되므로 이 효과가 같은 성공으로 두 번 실행되지 않는다
    }
  }, [state, stock.name, targetPrice, condition, side, quantity, onClose]);

  const total = Number(targetPrice || 0) * Number(quantity || 0);
  const accent = side === "BUY" ? "bg-up" : "bg-down";

  return (
    <dialog
      ref={dialogRef}
      onClose={onClose}
      // 바깥(어두운 배경)을 누르면 닫는다. target이 dialog 자신일 때만 = 안쪽 내용을 누른 게 아닐 때
      onClick={(event) => {
        if (event.target === event.currentTarget) event.currentTarget.close();
      }}
      className="m-auto w-[min(92vw,420px)] rounded-3xl bg-zinc-900 p-0 text-foreground backdrop:bg-black/60 backdrop:backdrop-blur-sm"
    >
      <form action={formAction} className="flex flex-col gap-5 p-6">
        <header className="flex items-center gap-3">
          <StockLogo symbol={stock.symbol} name={stock.name} logoUrl={stock.logoUrl} />
          <div>
            <h2 className="text-xl font-bold">{stock.name} 조건 주문</h2>
            <p className="text-sm text-zinc-400">
              {stock.symbol} · 현재가 <span className="font-semibold text-foreground">{formatWon(livePrice)}</span>
            </p>
          </div>
        </header>

        {/* 서버 액션에 함께 전달되는 값들 (사용자가 직접 입력하지 않는 것 포함) */}
        <input type="hidden" name="symbol" value={stock.symbol} />
        <input type="hidden" name="side" value={side} />
        <input type="hidden" name="condition" value={condition} />
        <input type="hidden" name="targetPrice" value={targetPrice} />
        <input type="hidden" name="quantity" value={quantity} />
        <input type="hidden" name="idempotencyKey" value={idempotencyKey} />

        <Segmented
          label="매수/매도"
          options={ORDER_SIDES.map((value) => ({ value, label: SIDE_LABEL[value] }))}
          selected={side}
          onSelect={setSide}
          activeClass={accent}
        />
        <Segmented
          label="발동 조건"
          options={TRIGGER_CONDITIONS.map((value) => ({ value, label: `목표가 ${CONDITION_LABEL[value]}` }))}
          selected={condition}
          onSelect={setCondition}
          activeClass="bg-zinc-600"
        />

        <Field label="목표 가격 (원)">
          <input
            inputMode="numeric"
            value={display(targetPrice)}
            onChange={(event) => setTargetPrice(digitsOnly(event.target.value))}
            placeholder="0"
            className="w-full bg-transparent text-right text-lg font-semibold tabular-nums outline-none"
          />
          <button
            type="button"
            onClick={() => setTargetPrice(String(livePrice))}
            className="shrink-0 rounded-lg bg-white/10 px-2 py-1 text-xs text-zinc-300 hover:bg-white/20"
          >
            현재가
          </button>
        </Field>

        <Field label="수량 (주)">
          <input
            inputMode="numeric"
            value={display(quantity)}
            onChange={(event) => setQuantity(digitsOnly(event.target.value))}
            placeholder="0"
            className="w-full bg-transparent text-right text-lg font-semibold tabular-nums outline-none"
          />
        </Field>

        <p className="rounded-2xl bg-white/5 p-4 text-sm leading-relaxed text-zinc-300">
          가격이 <b className="text-foreground">{formatWon(Number(targetPrice || 0))}</b>{" "}
          {CONDITION_PHRASE[condition]} <b className="text-foreground">{display(quantity) || 0}주</b>를{" "}
          <b className={side === "BUY" ? "text-up" : "text-down"}>{SIDE_LABEL[side]}</b>해요.
          <br />
          <span className="text-zinc-500">예상 금액 {formatWon(total)}</span>
        </p>

        {state.status === "error" && (
          <p role="alert" className="text-sm font-medium text-up">
            {state.message}
          </p>
        )}

        <div className="flex gap-3">
          <button
            type="button"
            onClick={() => dialogRef.current?.close()}
            className="h-12 flex-1 rounded-2xl bg-white/10 font-semibold hover:bg-white/15"
          >
            닫기
          </button>
          <button
            type="submit"
            disabled={pending}
            className={`h-12 flex-[2] rounded-2xl font-bold text-white transition-opacity disabled:opacity-50 ${accent}`}
          >
            {pending ? "등록 중…" : "조건 주문 등록"}
          </button>
        </div>
      </form>
    </dialog>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-sm text-zinc-400">{label}</span>
      <span className="flex items-center gap-2 rounded-2xl bg-white/5 px-4 py-3 focus-within:ring-2 focus-within:ring-down">
        {children}
      </span>
    </label>
  );
}

type SegmentedProps<T extends string> = {
  readonly label: string;
  readonly options: readonly { readonly value: T; readonly label: string }[];
  readonly selected: T;
  readonly onSelect: (value: T) => void;
  readonly activeClass: string;
};

/** 두세 개 중 하나를 고르는 토글 버튼. 제네릭(T)이라 호출하는 쪽의 유니온 타입이 그대로 유지된다. */
function Segmented<T extends string>({ label, options, selected, onSelect, activeClass }: SegmentedProps<T>) {
  return (
    <div role="radiogroup" aria-label={label} className="flex gap-1 rounded-2xl bg-white/5 p-1">
      {options.map((option) => {
        const active = option.value === selected;
        return (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => onSelect(option.value)}
            className={`h-10 flex-1 rounded-xl text-sm font-semibold transition-colors ${
              active ? `${activeClass} text-white` : "text-zinc-400 hover:text-zinc-200"
            }`}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
