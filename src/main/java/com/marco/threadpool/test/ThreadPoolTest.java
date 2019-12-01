package com.marco.threadpool.test;

import com.marco.threadpool.core.CustomThreadPool;
import com.marco.threadpool.core.DefaultRejectHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author : marco.zheng
 * @date : 2019/11/24 13:02
 */
public class ThreadPoolTest {

    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolTest.class);

    public static void main(String[] args) throws InterruptedException {
        BlockingQueue queue = new ArrayBlockingQueue(7);
        CustomThreadPool pool = new CustomThreadPool(3, 5, 1,
                TimeUnit.SECONDS, queue, new DefaultRejectHandler());
        for (int i = 0; i < 10; i++) {
            pool.execute(() -> System.out.println("run！！！"));
        }
        logger.info("休眠前线程活跃数:[{}]", pool.getNumOfWorkers());
        TimeUnit.SECONDS.sleep(5);
        logger.info("休眠后线程活跃数:[{}]", pool.getNumOfWorkers());
        for (int i = 0; i < 5; i++) {
            pool.execute(() -> System.out.println("run！！！"));
        }
        logger.info("最终线程活跃数:[{}]", pool.getNumOfWorkers());
        logger.info("----------------end------------------");
        pool.shutDown();

    }
}
