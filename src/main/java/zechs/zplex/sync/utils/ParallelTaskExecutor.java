package zechs.zplex.sync.utils;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.logging.Logger;

@Service
public class ParallelTaskExecutor {

    private static final Logger LOGGER = Logger.getLogger(ParallelTaskExecutor.class.getName());
    private final ThreadPoolTaskExecutor taskExecutor;
    private final ForkJoinPool forkJoinPool;

    @Autowired
    public ParallelTaskExecutor(@Qualifier("zplexSyncTaskExecutor") ThreadPoolTaskExecutor taskExecutor,
                                @Qualifier("zplexSyncThreadPoolSize") int threadPoolSize, ForkJoinPool forkJoinPool) {
        this.taskExecutor = taskExecutor;
        this.forkJoinPool = forkJoinPool;
        System.out.println("Thread-pool size is " + threadPoolSize);
    }

    public CompletableFuture<Void> executeTask(Runnable task) {
        return CompletableFuture.runAsync(task, taskExecutor);
    }

    public <T> T invokeForkJoinPool(RecursiveTask<T> task) {
        return forkJoinPool.invoke(task);
    }

    @PreDestroy
    public void cleanUp() {
        taskExecutor.shutdown();
        LOGGER.info("Cleaning up task service...");
    }
}
