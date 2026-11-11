-- TickLock 초기 스키마.
-- 금액/가격은 전부 BIGINT(원 단위 정수): 소수점 오차를 원천 차단한다.
-- 돈이 오가는 테이블은 FK를 엄격히(ON DELETE RESTRICT), 대량 INSERT 로그(price_ticks)는 FK 없이.

CREATE TABLE users (
    id          BIGSERIAL    PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE stocks (
    symbol      VARCHAR(16) PRIMARY KEY,                    -- 예: '005930'
    name        VARCHAR(64) NOT NULL,
    base_price  BIGINT      NOT NULL CHECK (base_price > 0) -- 시세 생성기의 시작 가격
);

-- 지갑 (유저당 1개)
CREATE TABLE wallets (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL UNIQUE REFERENCES users(id) ON DELETE RESTRICT,
    balance     BIGINT      NOT NULL CHECK (balance >= 0),  -- 락이 뚫려도 잔액 음수는 DB가 막는 최후의 안전망
    version     BIGINT      NOT NULL DEFAULT 0,             -- 낙관적 락(@Version)
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE holdings (
    wallet_id   BIGINT      NOT NULL REFERENCES wallets(id)   ON DELETE RESTRICT,
    symbol      VARCHAR(16) NOT NULL REFERENCES stocks(symbol) ON DELETE RESTRICT,
    quantity    BIGINT      NOT NULL CHECK (quantity >= 0),
    PRIMARY KEY (wallet_id, symbol)
);

-- 조건 주문 ("삼성전자 70,000원 이하이면 매수")
CREATE TABLE trigger_orders (
    id                BIGSERIAL   PRIMARY KEY,
    user_id           BIGINT      NOT NULL REFERENCES users(id)      ON DELETE RESTRICT,
    wallet_id         BIGINT      NOT NULL REFERENCES wallets(id)    ON DELETE RESTRICT,
    symbol            VARCHAR(16) NOT NULL REFERENCES stocks(symbol) ON DELETE RESTRICT,
    side              VARCHAR(4)  NOT NULL CHECK (side IN ('BUY', 'SELL')),
    trigger_condition VARCHAR(3)  NOT NULL CHECK (trigger_condition IN ('LTE', 'GTE')), -- 이하 / 이상
    target_price      BIGINT      NOT NULL CHECK (target_price > 0),
    quantity          BIGINT      NOT NULL CHECK (quantity > 0),
    status            VARCHAR(10) NOT NULL DEFAULT 'ACTIVE'
                      CHECK (status IN ('ACTIVE', 'EXECUTED', 'FAILED', 'CANCELED')),
    request_key       VARCHAR(64) NOT NULL,   -- 클라이언트 재시도로 같은 주문이 두 번 등록되는 것을 막는다
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, request_key)
);

-- 시세가 들어올 때 "이 종목의 활성 주문"만 빠르게 찾기 위한 부분 인덱스
CREATE INDEX idx_orders_active ON trigger_orders (symbol, target_price) WHERE status = 'ACTIVE';

-- 체결 내역. trigger_order_id UNIQUE = "주문 1건당 체결은 최대 1번"을 DB가 보장 (멱등성의 최종 방어선)
CREATE TABLE executions (
    id                BIGSERIAL   PRIMARY KEY,
    trigger_order_id  BIGINT      NOT NULL UNIQUE REFERENCES trigger_orders(id) ON DELETE RESTRICT,
    wallet_id         BIGINT      NOT NULL REFERENCES wallets(id)    ON DELETE RESTRICT,
    symbol            VARCHAR(16) NOT NULL REFERENCES stocks(symbol) ON DELETE RESTRICT,
    price             BIGINT      NOT NULL CHECK (price > 0),
    quantity          BIGINT      NOT NULL CHECK (quantity > 0),
    executed_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 시세 로그: 초당 대량 INSERT 대상이라 FK를 걸지 않는다.
CREATE TABLE price_ticks (
    id      BIGSERIAL,
    symbol  VARCHAR(16) NOT NULL,
    price   BIGINT      NOT NULL,
    volume  BIGINT      NOT NULL,
    ts      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id, ts)
);
CREATE INDEX idx_ticks_symbol_ts ON price_ticks (symbol, ts DESC);
