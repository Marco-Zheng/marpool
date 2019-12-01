package com.marco.threadpool.core;

/**
 * @author : marco.zheng
 * @date :  15:31
 */
public interface RejectHandler {

    void rejectedExecution(Runnable runnable, CustomThreadPool customThreadPool);
}
