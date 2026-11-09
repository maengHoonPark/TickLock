package com.ticklock.execution;

import com.ticklock.order.ActiveOrder;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 안전한 체결기: 지갑 단위 분산 락(Redisson) + DB 트랜잭션.
 *
 * 순서가 생명이다:  락 획득 → 트랜잭션 시작 → 커밋 → 락 해제
 *  락을 트랜잭션 "안"에서 잡거나 커밋 "전"에 풀면, 다음 스레드가 아직 커밋되지 않은 옛 잔액을 읽어
 *  락을 걸고도 같은 문제가 생긴다. 이 클래스에는 @Transactional이 없고, 트랜잭션은 SafeExecutionTransaction이 담당한다.
 *
 * 왜 "분산" 락인가?
 *  서버가 여러 대로 늘어나면 JVM 안의 synchronized/ReentrantLock은 서버 사이를 막지 못한다.
 *  Redis에 락을 두면 몇 대가 떠 있든 같은 지갑은 한 번에 한 곳에서만 처리된다.
 *
 * 락 키를 "지갑 단위"로 잡은 이유: 같은 지갑만 줄 세우고, 다른 유저의 체결은 서로 막지 않아 병렬로 처리된다.
 */
@Component
@ConditionalOnProperty(name = "ticklock.execution.mode", havingValue = "safe", matchIfMissing = true)
public class SafeOrderExecutor implements OrderExecutor {

    private static final Logger log = LoggerFactory.getLogger(SafeOrderExecutor.class);

    private static final String LOCK_KEY_PREFIX = "lock:wallet:";
    private static final long LOCK_WAIT_SECONDS = 5;

    private final RedissonClient redisson;
    private final SafeExecutionTransaction transaction;

    public SafeOrderExecutor(RedissonClient redisson, SafeExecutionTransaction transaction) {
        this.redisson = redisson;
        this.transaction = transaction;
    }

    @Override
    public ExecutionResult execute(ActiveOrder order, long currentPrice) {
        RLock lock = redisson.getLock(LOCK_KEY_PREFIX + order.walletId());
        boolean locked = false;
        try {
            // 최대 5초 기다린다. 만료 시간(leaseTime)을 지정하지 않으면 Redisson의 "워치독"이 작동해서,
            // 작업이 끝나기 전에는 락을 자동 연장하고, 서버가 죽으면 연장이 멈춰 락이 저절로 풀린다(영원히 잠기지 않음).
            locked = lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new LockNotAcquiredException(order.walletId());
            }
            return transaction.execute(order, currentPrice); // 이 호출이 반환될 때는 이미 커밋이 끝나 있다
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 인터럽트 신호를 보존한다 (서버 종료 중일 수 있음)
            throw new LockNotAcquiredException(order.walletId());
        } catch (DuplicateKeyException e) {
            // 최후 방어선: 같은 주문의 체결 기록이 이미 있다(executions.trigger_order_id UNIQUE). 트랜잭션은 롤백됐다.
            log.warn("이미 체결된 주문의 중복 체결 시도를 DB 제약이 막았습니다. orderId={}", order.id());
            return new ExecutionResult.Skipped(order.id());
        } finally {
            // 내가 잡은 락만 푼다. (락이 만료돼 다른 스레드가 잡았는데 내가 풀어버리는 사고를 막는다)
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
