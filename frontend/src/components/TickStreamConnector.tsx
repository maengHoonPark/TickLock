"use client";

import { useTickStream } from "@/lib/useTickStream";

/**
 * 실시간 스트림 연결을 앱 전체에서 "한 번만" 유지하는 보이지 않는 컴포넌트 (layout에 둔다).
 *
 * 화면(홈/종목 상세)마다 연결을 열고 닫으면, 화면을 옮길 때마다 연결이 끊겼다 붙으며 시세와 체결 알림을 놓칠 수 있다.
 * layout은 화면이 바뀌어도 사라지지 않으므로 여기서 연결하면 어느 화면에서나 같은 연결을 계속 쓴다.
 */
export function TickStreamConnector() {
  useTickStream();
  return null;
}
