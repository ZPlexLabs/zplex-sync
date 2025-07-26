package zechs.zplex.sync.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ForkJoinPool;

@Configuration
public class ThreadPoolConfig {

    @Bean
    @Qualifier("zplexSyncThreadPoolSize")
    public int getThreadPoolSize() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int threadPoolSize = availableProcessors * 2; // allowing some level of context switching
        return threadPoolSize;
    }

    @Bean
    @Qualifier("zplexSyncTaskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(getThreadPoolSize());
        executor.setMaxPoolSize(getThreadPoolSize());
        executor.setThreadNamePrefix("ZPlexSyncThread-");
        executor.setKeepAliveSeconds(60);
        executor.initialize();
        return executor;
    }

    @Bean
    @Qualifier("zplexForkJoinPool")
    public ForkJoinPool forkJoinPool(@Qualifier("zplexSyncThreadPoolSize") int parallelism) {
        return new ForkJoinPool(parallelism);
    }

}
