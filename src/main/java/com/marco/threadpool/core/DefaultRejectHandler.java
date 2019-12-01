package com.marco.threadpool.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;

/**
 * 默认拒绝策略执行器
 * @author : marco.zheng
 * @date :  16:36
 */
public class DefaultRejectHandler implements RejectHandler {

    private static final Logger logger = LoggerFactory.getLogger(DefaultRejectHandler.class);
    /**
     * Creates a {@code DefaultRejectHandler}.
     */
    public DefaultRejectHandler() {}

    @Override
    public void rejectedExecution(Runnable runnable, CustomThreadPool customThreadPool) {

        if (!customThreadPool.isShutDown()) {
            BlockingQueue<Runnable> queue = customThreadPool.getWorkQueue();
            logger.debug("Task execution queued");
            if(!queue.offer(runnable)) {
                throw new RejectedExecutionException("the maximum number of threads has been reached");
            }
        } else {
            throw new RejectedExecutionException("Executor has been shut down");
        }
    }
}
