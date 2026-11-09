-- 데모 데이터 초기화: 부하 테스트를 처음부터 다시 돌리기 전에 실행한다.
-- (FK 때문에 자식 테이블부터 지운다: executions → trigger_orders, holdings)
DELETE FROM executions;
DELETE FROM trigger_orders;
DELETE FROM holdings;
UPDATE wallets SET balance = 100000000;
