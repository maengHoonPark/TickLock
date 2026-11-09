"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { toast } from "sonner";
import { usePriceStore } from "@/stores/priceStore";
import { parseServerMessage, type ServerMessage } from "@/types/messages";
import { SIDE_LABEL } from "@/types/order";
import { API_BASE_URL } from "./config";
import { formatWon } from "./format";

/** 체결이 한꺼번에 여러 건 와도 서버 데이터(잔액·주문 목록) 새로고침은 이 간격으로 한 번만 한다. */
const REFRESH_DEBOUNCE_MS = 300;

/**
 * 서버의 SSE 스트림에 연결해 시세를 스토어에 반영하고, 체결/실패 알림을 토스트로 보여준다.
 * EventSource는 연결이 끊기면 브라우저가 알아서 재연결해 주므로 재시도 코드를 직접 짤 필요가 없다.
 */
export function useTickStream(): void {
  const router = useRouter();

  useEffect(() => {
    const source = new EventSource(`${API_BASE_URL}/api/stream`);
    const { setConnected } = usePriceStore.getState();
    let refreshTimer: ReturnType<typeof setTimeout> | undefined;

    /** 잔액·주문 목록은 서버 컴포넌트가 가져오므로, 체결이 일어나면 서버 데이터를 다시 받아오게 한다. */
    const scheduleRefresh = () => {
      clearTimeout(refreshTimer);
      refreshTimer = setTimeout(() => router.refresh(), REFRESH_DEBOUNCE_MS);
    };

    /** 메시지 종류별 처리. switch + never로, 새 메시지 타입을 추가하고 처리를 빼먹으면 컴파일 에러가 난다. */
    const handleMessage = (message: ServerMessage): void => {
      switch (message.type) {
        case "tick":
          usePriceStore.getState().applyTick(message.symbol, message.price);
          return;
        case "executed":
          toast.success(`${message.name} ${SIDE_LABEL[message.side]} 체결`, {
            description: `${formatWon(message.price)} · ${message.quantity}주`,
          });
          scheduleRefresh();
          return;
        case "failed":
          toast.error(`${message.name} 체결 실패`, { description: message.reason });
          scheduleRefresh();
          return;
        default: {
          const unreachable: never = message;
          return unreachable;
        }
      }
    };

    source.onopen = () => setConnected(true);
    // 끊기면 브라우저가 자동 재연결을 시도한다. 그동안 화면엔 "연결 중"으로 보여준다.
    source.onerror = () => setConnected(false);
    source.onmessage = (event: MessageEvent<string>) => {
      const message = parseServerMessage(event.data);
      if (message) handleMessage(message); // 형식이 틀린 메시지는 조용히 무시
    };

    // 컴포넌트가 사라질 때 연결과 대기 중인 타이머를 정리한다.
    return () => {
      source.close();
      clearTimeout(refreshTimer);
      setConnected(false);
    };
  }, [router]);
}
