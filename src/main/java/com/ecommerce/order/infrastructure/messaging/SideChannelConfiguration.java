package com.ecommerce.order.infrastructure.messaging;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Gives the event side-channel its own small thread pool, separate from the HTTP request threads.
 * The queue is bounded: under overload a new event is rejected (and logged by the publisher adapter)
 * instead of running on the request thread, so the REST response time never depends on the side-channel.
 */
@Configuration
@EnableAsync
public class SideChannelConfiguration {

    public static final String EXECUTOR = "sideChannelExecutor";
    public static final String THREAD_NAME_PREFIX = "side-channel-";

    @Bean(name = EXECUTOR)
    public ThreadPoolTaskExecutor sideChannelExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
