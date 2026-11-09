package com.ticklock.execution;

/**
 * 지갑 락을 제한 시간 안에 얻지 못했다. 일시적인 혼잡이라서 "주문을 실패시키지 않고" 나중에 다시 시도해야 한다.
 * (일반 RuntimeException과 구분해 두면, 받는 쪽이 "재시도할 오류"와 "진짜 오류"를 나눠 처리할 수 있다.)
 */
public class LockNotAcquiredException extends RuntimeException {

    public LockNotAcquiredException(long walletId) {
        super("지갑 락을 얻지 못했습니다. walletId=" + walletId);
    }
}
