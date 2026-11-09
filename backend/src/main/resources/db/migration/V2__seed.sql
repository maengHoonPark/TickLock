-- 개발용 초기 데이터: 종목 + 데모 유저(잔액 1억원)

INSERT INTO stocks (symbol, name, base_price) VALUES
    ('005930', '삼성전자',       72000),
    ('000660', 'SK하이닉스',    180000),
    ('035420', 'NAVER',         210000),
    ('035720', '카카오',         48000),
    ('005380', '현대차',        240000);

INSERT INTO users (email) VALUES ('demo@ticklock.dev');

INSERT INTO wallets (user_id, balance)
SELECT id, 100000000 FROM users WHERE email = 'demo@ticklock.dev';
