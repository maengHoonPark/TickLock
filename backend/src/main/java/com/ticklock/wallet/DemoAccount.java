package com.ticklock.wallet;

import org.springframework.stereotype.Component;

/**
 * 로그인 기능이 생기기 전까지 "현재 사용자"를 대신하는 데모 계정.
 *
 * 인증은 이 프로젝트의 핵심(동시성/시세 처리)이 아니라서 일단 시드 데이터의 데모 유저 1명을 쓴다.
 * 나중에 로그인을 붙이면 이 클래스만 "토큰에서 꺼낸 유저"로 바꾸면 되도록, 호출하는 쪽은 이 클래스만 바라본다.
 */
@Component
public class DemoAccount {

    private static final String EMAIL = "demo@ticklock.dev";

    /** 바뀌지 않는 식별자만 담은 값. 잔액처럼 계속 바뀌는 값은 일부러 넣지 않았다. */
    public record Identity(long userId, long walletId) {
    }

    private final AppUserRepository userRepository;
    private final WalletRepository walletRepository;

    /** 첫 조회 결과를 기억해 둔다. 유저/지갑 ID는 바뀌지 않으므로 요청마다 DB를 읽을 필요가 없다. */
    private volatile Identity identity;

    public DemoAccount(AppUserRepository userRepository, WalletRepository walletRepository) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
    }

    /** 유저/지갑 ID만 필요할 때 사용한다 (주문 등록·취소·조회). 캐시되어 있어 DB를 읽지 않는다. */
    public Identity identity() {
        Identity cached = identity;
        if (cached == null) {
            Wallet wallet = wallet();
            cached = new Identity(wallet.getUserId(), wallet.getId());
            identity = cached;
        }
        return cached;
    }

    /** 최신 잔액이 필요할 때 사용한다. 매번 DB에서 읽는다. */
    public Wallet wallet() {
        AppUser user = userRepository.findByEmail(EMAIL)
                .orElseThrow(() -> new IllegalStateException("데모 유저가 없습니다. 시드 마이그레이션을 확인하세요."));
        return walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("데모 유저의 지갑이 없습니다."));
    }
}
