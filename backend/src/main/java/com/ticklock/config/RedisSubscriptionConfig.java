package com.ticklock.config;

import com.ticklock.tick.TickChannel;
import com.ticklock.tick.TickSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 【이 파일이 하는 일】
 * Redis의 "ticks" 채널에 시세 메시지가 도착하면, TickSubscriber(받아서 처리하는 담당자)를 호출해 주는
 * "수신기(RedisMessageListenerContainer)"를 만들어서 Spring에 등록한다.
 *
 * 【비유】 택배 센터
 *  - 수신기      = 택배 센터 (Redis에서 오는 택배를 받는다)
 *  - 일꾼(스레드) = 택배를 나르는 직원
 *  - TickSubscriber = 택배를 받아서 실제로 처리하는 담당 부서
 *
 * 【왜 일꾼을 "1명"으로 정했나】
 * 일꾼이 여러 명이면 뒤에 온 택배를 먼저 나르는 일이 생긴다. 그러면 최신 시세가 "과거 가격"으로 덮어써진다.
 * 실제로 측정해 보니 약 4만 건 중 179건이 순서가 뒤바뀌었고, 일꾼을 1명으로 정한 뒤에는 0건이 되었다.
 * (1명이어도 충분히 빠르다. 이 일꾼은 "받아서 담당 부서에 넘기기"만 하고, 느린 일은 다른 곳에서 한다.)
 */
@Configuration // "이 클래스는 Spring이 사용할 객체를 만드는 방법을 적어 둔 설정서"라는 표시
public class RedisSubscriptionConfig {

    /**
     * @Bean = "이 메서드가 돌려주는 객체를 Spring 보관함에 넣어라".
     *
     * 이 메서드는 우리가 호출하지 않는다. Spring이 서버를 시작할 때 자동으로 한 번 호출한다.
     * 괄호 안의 두 개는 "이 도구 두 개가 필요하다"는 주문서이고, Spring이 보관함에서 찾아서 채워 준다.
     *  - connectionFactory: Redis에 접속하는 도구 (주소는 application.yml에 적혀 있고, Spring이 만들어 둠)
     *  - subscriber       : 시세 메시지를 받아서 처리하는 담당자 (TickSubscriber 클래스의 객체)
     */
    @Bean
    RedisMessageListenerContainer tickListenerContainer(RedisConnectionFactory connectionFactory,
                                                        TickSubscriber subscriber) {
        // 1) 빈 수신기를 만든다
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();

        // 2) 어느 Redis에 연결할지 알려 준다
        container.setConnectionFactory(connectionFactory);

        // 3) 메시지를 처리할 일꾼을 정해 준다: 1명 (아래 createSingleWorker 참고)
        container.setTaskExecutor(createSingleWorker());

        // 4) "구독을 유지하는 일"을 맡을 별도의 일꾼을 정해 준다.
        //    메시지 처리 일꾼과 같은 사람이 하면 구독 일 때문에 메시지 처리가 막힐 수 있어서 따로 둔다.
        container.setSubscriptionExecutor(new SimpleAsyncTaskExecutor("tick-subscription-"));

        // 5) "ticks 채널에 메시지가 오면 subscriber에게 넘겨라"라고 연결한다
        container.addMessageListener(subscriber, new ChannelTopic(TickChannel.NAME));

        return container;
    }

    /**
     * 일꾼(스레드)을 딱 1명만 두는 "일꾼 관리자"를 만든다.
     * 일이 여러 개 들어오면 이 일꾼 1명이 들어온 순서대로 하나씩 처리한다.
     */
    private ThreadPoolTaskExecutor createSingleWorker() {
        ThreadPoolTaskExecutor worker = new ThreadPoolTaskExecutor();

        worker.setCorePoolSize(1); // 기본 일꾼 수: 1명
        worker.setMaxPoolSize(1);  // 아무리 일이 많아도 최대 1명 (늘리지 않는다) → 순서가 보장된다
        worker.setThreadNamePrefix("tick-dispatch-"); // 일꾼 이름표: 로그에서 "시세 처리 일꾼"을 알아보려고
        worker.setDaemon(true);    // 서버를 끌 때 이 일꾼이 일을 마칠 때까지 기다리느라 종료가 지연되지 않게 한다

        worker.initialize();       // 위 설정대로 일꾼 관리자를 준비시킨다 (이걸 해야 실제로 일꾼이 생긴다)
        return worker;
    }
}
