package com.ticklock.config;

import com.ticklock.tick.TickChannel;
import com.ticklock.tick.TickSubscriber;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis Pub/Sub 구독 설정.
 * 컨테이너가 채널을 듣고 있다가, 메시지가 오면 TickSubscriber.onMessage를 호출한다.
 *
 * ⚠️ 메시지를 처리하는 스레드를 "1개"로 고정한 이유 (측정으로 발견한 문제)
 *  기본 설정은 메시지 1건마다 새 스레드를 만들어 처리한다. 스레드들이 동시에 달리면 나중에 발행된 시세가
 *  먼저 처리될 수 있어서, 최신가 캐시가 "과거 가격으로 되돌아가는" 일이 생긴다. (수정 전 측정: 약 4만 건 중 179건 역전)
 *  게다가 초당 1,000개의 스레드를 만들고 버리는 비용도 든다.
 *  단일 스레드로 처리하면 발행 순서 그대로 처리되고, 이 스레드는 "판단+접수"만 하므로(느린 일은 다른 스레드로 넘김) 충분히 빠르다.
 */
@Configuration
public class RedisSubscriptionConfig {

    @Bean
    RedisMessageListenerContainer tickListenerContainer(RedisConnectionFactory connectionFactory,
                                                         TickSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(singleThreadDispatcher());
        // 구독 작업은 메시지 처리와 별개 스레드에서 돌려야 한다. 같은 단일 스레드를 쓰면 구독이 스레드를 붙잡을 수 있다.
        container.setSubscriptionExecutor(new SimpleAsyncTaskExecutor("tick-subscription-"));
        container.addMessageListener(subscriber, new ChannelTopic(TickChannel.NAME));
        return container;
    }

    private Executor singleThreadDispatcher() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "tick-dispatch");
            thread.setDaemon(true); // 서버 종료를 이 스레드가 막지 않게 한다
            return thread;
        });
    }
}
