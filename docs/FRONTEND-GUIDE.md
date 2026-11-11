# 프론트엔드 읽는 법 (Next.js를 몰라도 되는 안내서)

> 전부 읽을 필요 없다. **"주문 1건이 들어가서 체결되는 흐름"을 따라가면서 필요한 파일만** 보면 된다.
> 화면을 예쁘게 만드는 파일(차트, 숫자 롤링 등)은 건너뛰어도 된다.

---

## 1. Next.js를 이것만 알면 읽힌다 (5가지)

| 개념 | 한 줄 설명 | 비유 |
|---|---|---|
| **폴더 = 주소** | `src/app/page.tsx`는 `/`, `src/app/stocks/[symbol]/page.tsx`는 `/stocks/005930` 화면이 된다. `[symbol]`은 주소의 변하는 부분 | 폴더 구조가 곧 URL 목록 |
| **layout.tsx** | 모든 화면을 감싸는 공통 틀 (헤더, 토스트, 실시간 연결을 여기에 둠) | 모든 페이지가 공유하는 액자 |
| **서버 컴포넌트 (기본)** | 서버에서 데이터를 가져와 완성된 화면을 만들어 보낸다. 상태(useState)를 못 쓴다 | 서버가 요리해서 내오는 완성 요리 |
| **`"use client"`** | 파일 맨 위에 이 줄이 있으면 브라우저에서 실행된다. 클릭, 입력, 실시간 갱신처럼 "움직이는 것"은 여기서 | 손님 앞에서 즉석으로 만드는 요리 |
| **서버 액션 (`"use server"`)** | 폼 제출을 서버 함수가 직접 받는다. 별도 API 주소를 안 만들어도 된다 | 폼 → 서버 함수 직통 연결 |

**React 기본**도 조금 쓴다:
- 컴포넌트 = 화면 조각을 만드는 함수 (`function StockRow() { return <li>...</li> }`)
- `useState` = 화면이 기억하는 값, 바뀌면 화면이 다시 그려진다
- `useEffect` = "화면이 나타났을 때 한 번 실행할 일" (예: 실시간 연결 시작)
- `className="text-up ..."` = Tailwind. 글자색·여백 같은 스타일을 클래스 이름으로 지정

---

## 2. 폴더 지도 (`frontend/src/`)

```
app/                     화면(주소)
├─ layout.tsx            공통 틀: 헤더 + 실시간 연결 + 토스트
├─ page.tsx              홈 ("/")        시세·보유·주문·체결 탭
├─ stocks/[symbol]/      종목 상세 ("/stocks/005930")
├─ debug/                개발용 관찰 화면 ("/debug")
├─ actions.ts            서버 액션: 주문 등록/취소를 백엔드로 전달
└─ globals.css           전체 스타일, 깜빡임 애니메이션

components/              화면 조각들 (버튼, 목록 한 줄, 팝업 등)
stores/priceStore.ts     시세를 모아 두는 "공용 저장소" (Zustand)
lib/                     보조 코드: API 호출, 포맷, 실시간 연결
types/                   값의 모양 정의 (주문 상태, 서버 메시지)
```

---

## 3. 따라가며 읽기: "주문 1건" 여행

화면에서 백엔드까지 이 순서로 파일이 이어진다. **이 순서대로만 읽으세요.**

| 순서 | 파일 | 여기서 일어나는 일 |
|---|---|---|
| ① | `components/OrderDialog.tsx` | 팝업 폼. 가격·수량을 입력하고 "등록" 버튼을 누른다 |
| ② | `app/actions.ts` | 폼 값을 받아 **검사(zod)** 한 뒤 백엔드 `POST /api/orders`를 호출 |
| ③ | (백엔드) `OrderController` → `OrderService` → `OrderRepository` | 주문을 검증하고 DB에 저장 (`docs/CODE-GUIDE.md` 참고) |
| ④ | (백엔드) `OrderMatcher` → `SafeOrderExecutor` | 시세가 조건에 맞으면 체결 |
| ⑤ | `lib/useTickStream.ts` | 백엔드가 보낸 "체결됨" 메시지를 받아 **토스트** 띄우고 화면 갱신 |

그 다음에 "시세가 화면에 나오는 흐름"을 본다.

| 순서 | 파일 | 여기서 일어나는 일 |
|---|---|---|
| ⑥ | `lib/useTickStream.ts` | 백엔드 시세 스트림(SSE)에 연결해서 시세가 올 때마다 저장소를 갱신 |
| ⑦ | `stores/priceStore.ts` | 종목별 최신 가격과 최근 가격 기록을 보관 |
| ⑧ | `components/StockRow.tsx` | 저장소에서 **자기 종목 가격만** 꺼내 목록 한 줄을 그림 |
| ⑨ | `app/page.tsx` | 홈 화면 조립: 자산 카드 + 탭 + 각 목록 |

**여기까지가 필수.** 나머지는 필요할 때만 보면 된다.

---

## 4. 건너뛰어도 되는 파일

| 파일 | 하는 일 | 읽어야 하는 때 |
|---|---|---|
| `RollingNumber.tsx` | 숫자가 롤링하며 바뀌는 효과 | 애니메이션이 궁금할 때 |
| `AreaChart.tsx`, `Sparkline.tsx` | 차트 그리기 (SVG) | 차트를 고치고 싶을 때 |
| `StockLogo.tsx`, `lib/stockBrand.ts` | 종목 아이콘 | 아이콘을 바꿀 때 |
| `DebugPanel.tsx`, `lib/debugApi.ts` | 개발용 관찰 화면 | 디버깅할 때 |
| `HoldingsPanel.tsx`, `ExecutionList.tsx` | 보유/체결 탭 | 해당 화면을 고칠 때 |

---

## 5. 파일을 열면 이렇게 읽으세요

1. **맨 위 주석**: "왜 이렇게 만들었는지"가 적혀 있다.
2. **`import` 줄**: 이 파일이 어떤 다른 파일을 쓰는지 알려준다 (여기서 Ctrl+클릭으로 이동).
3. **`export function ...`**: 이 파일의 본체. `return ( <... /> )` 안이 화면 모양이다.
4. 모르는 `use...`가 나오면 위 1번 표(React 기본)를 본다.

## 6. 직접 해보기 (3분짜리)

1. `components/StockRow.tsx`에서 `text-[17px]`를 `text-[22px]`로 바꾸고 저장 → 브라우저의 종목 이름이 커진다 (저장하면 자동 반영).
2. `app/page.tsx`의 `"모든 시세는 가상으로 만든 값이에요..."` 문구를 바꿔 보기.
3. `types/order.ts`의 `STATUS_LABEL`에서 `ACTIVE: "대기 중"`을 `"기다리는 중"`으로 바꾸기 → 주문 목록 배지가 바뀐다.
