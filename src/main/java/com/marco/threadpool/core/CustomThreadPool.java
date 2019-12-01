package com.marco.threadpool.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class CustomThreadPool {

	private static final Logger logger = LoggerFactory.getLogger(CustomThreadPool.class);

    private static final ReentrantLock mainLock = new ReentrantLock();
	/**
	 * 核心线程数
	 */
	private volatile int coreTheadSize;
	/**
	 * 最大线程数
	 */
	private volatile int maxSize;
	/**
	 * 线程最长存活时间(保活时间)
	 */
	private long keepAliveTime;
	/**
	 * 时间单位
	 */
	private TimeUnit unit;
	/**
	 * 等待队列
	 */
	private BlockingQueue<Runnable> workQueue;

	/**
	 * 存放线程池
	 */
	private volatile Set<Worker> workers;

    /**
     * 线程池中的总任务数
     */
	private AtomicInteger totalTask = new AtomicInteger(0);

    /**
     * 线程池是否关闭
     */
	private AtomicBoolean isShutDown = new AtomicBoolean(false);

    /**
     * 拒绝线程通知器
     */
    private volatile RejectHandler handler;

    /**
     * 线程池被shutdown功能中，唤醒线程的锁
     */
    private Object shutDownNotify = new Object();


	public CustomThreadPool() {}

	/**
	 * @param coreTheadSize 核心线程数
	 * @param maxSize 最大线程数
	 * @param keepAliveTime 保活时间
	 * @param unit 单位
	 * @param workQueue 等待队列
	 * @param handler 拒绝接收线程执行器
	 */
	public CustomThreadPool(int coreTheadSize, int maxSize, long keepAliveTime, TimeUnit unit,
			BlockingQueue<Runnable> workQueue, RejectHandler handler) {
		if (coreTheadSize < 0 ||
				maxSize <= 0 ||
				keepAliveTime < 0)
			throw new IllegalArgumentException();
		if (workQueue == null)
			throw new NullPointerException();
		this.coreTheadSize = coreTheadSize;
		this.maxSize = maxSize;
		this.keepAliveTime = keepAliveTime;
		this.unit = unit;
		this.workQueue = workQueue;
		this.handler = handler;
		// 初始化worker线程池
		this.workers = new ConcurrentHashSet();
	}

    /**
     * 有返回值
     *
     * @param callable
     * @param <T>
     * @return
     */
    public <T> Future<T> submit(Callable<T> callable) {
        FutureTask<T> future = new FutureTask(callable);
        execute(future);
        return future;
    }

	/**
	 * 线程池是否被关闭
	 * @return
	 */
	public Boolean isShutDown() {
    	return isShutDown.get();
	}

    /**
     * 安排线程池中的线程执行请求，这一步主要的目的是线程的分配、调度
     * @param runnable 需要执行的请求or任务
     */
	public void execute(Runnable runnable) {
		if(null == runnable) {
			throw new NullPointerException("runnable can't be null!");
		}
		// 已提交线程计数
		totalTask.incrementAndGet();
		// 小于核心线程数，则创建线程并执行任务
		if(workers.size() < coreTheadSize) {
			addWorker(runnable);
			return;
		}
		// 判断当前的workQueue是否可加，如果不可加则返回false，可加则添加到workQueue
		boolean offer = workQueue.offer(runnable);
		if(!offer) {
			// 创建新的worker，直到maxSize
			if(workers.size() < maxSize) {
				addWorker(runnable);
			} else {
				logger.error("Exceeding the max thread size!");
				// 如果超出最大可创建线程数，则拒绝
				reject(runnable);
//				try {
//					// 如果超出最大可创建线程数，则阻塞，直到队列中有空位再入位
//					workQueue.put(runnable);
//				} catch (InterruptedException e) {
//
//				}
			}
		}

	}

	/**
	 * 拒绝接收线程
	 * @param runnable
	 */
	private void reject(Runnable runnable) {
		handler.rejectedExecution(runnable, this);
	}

	/**
	 * 创建新的工作线程执行任务，该过程需要加锁
	 * @param runnable 需要执行的请求or任务
	 */
	private void addWorker(Runnable runnable) {
	    // param1：需要执行的任务 param2：是否是新的任务
		// 此处可以添加ThreadFactory来创建worker
		Worker worker = new Worker(runnable,true);
		// 执行任务
		worker.startTask();
		// 将新创建的线程置入线程池(ConcurrentHashSet)
		workers.add(worker);
	}

	/**
	 * 从workQueue中获取任务
	 * @return
	 */
	private Runnable getTask() {
		ReentrantLock mainLock = this.mainLock;
		// 关闭标识以及任务是否全部完成了
		if(isShutDown.get() && totalTask.get() == 0) {
			return null;
		}
		// 获取任务时可能涉及到并发获取，因此需要对此操作上锁
		mainLock.lock();
		try {
			Runnable task = null;
			if(workers.size() > coreTheadSize) {
				// 当大于核心线程数量时需要用保活时间获取任务
				// (取走BlockingQueue里排在首位的对象,若不能立即取出,则可以等time参数规定的时间,取不到时返回null)
				task = workQueue.poll(keepAliveTime, unit);
			} else {
				// 取走BlockingQueue里排在首位的对象,若BlockingQueue为空,阻断进入等待状态直到Blocking有新的对象被加入为止
				task = workQueue.take();
			}
			if(null != task) {
				return task;
			}
		} catch (InterruptedException e) {
			logger.error("current task is null");
			return null;
		} finally {
			mainLock.unlock();
		}
		return null;
	}

	/**
	 * 任务执行完成，正常关闭线程池
	 */
	public void shutDown() {
        synchronized (shutDownNotify) {
            while (totalTask.get() > 0) {
                try {
                	// 如果线程池中还有任务执行中，则先等一等，知道有通知再shutDown
                    shutDownNotify.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                }
            }
            shutDownNotify.notify();
        }
        isShutDown.set(true);
        tryClose(true);
	}

    /**
     * 强制关闭线程池，不管任务有没有执行完成，都强制关掉，可能会造成任务的丢失
     */
    public void forceShutDown() {
        isShutDown.set(true);
        tryClose(false);
    }

	/**
	 * 是否尝试关闭线程池
	 * true 当还有线程在工作时，等待线程工作完成，再关闭
	 * false 无论是否有线程在工作，都直接关闭
	 * @param isTry
	 */
	private void tryClose(boolean isTry) {
		if(!isTry) {
			closeAllTask();
		} else {
			if(isShutDown.get() && totalTask.get() == 0) {
				closeAllTask();
			}
 		}
	}

	/**
	 * 关闭所有的任务
	 */
	private void closeAllTask() {
        // 这里使用锁的目的是防止当系统shutdown时，退出的线程并发地中断没有被interrupted的线程
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
		try {
            for (Worker worker : workers) {
                worker.closeTask();
            }
        } finally {
		    mainLock.unlock();
        }
	}

    /**
     * 获取当前的工作线程数量
     * @return
     */
	public int getNumOfWorkers() {
        return workers.size();
    }

    /**
     * 线程池中的工作线程，真正执行任务的对象
     */
    private final class Worker extends Thread{

        Runnable task;

        final Thread thread;

        /**
         * 是否是新的任务
         * true 创建线程并执行任务
         * false 从队列中获取线程并执行
         */
        private boolean isNewTask;

        public Worker(Runnable task, boolean isNewTask) {
            this.task = task;
            this.isNewTask = isNewTask;
            thread = this;
        }
        /**
         * 开始执行任务，start会触发 run方法
         */
        public void startTask() {
            thread.start();
        }
        /**
         * 中断任务(设置中断标记)
         */
        public void closeTask() {
            thread.interrupt();
        }

        @Override
        public void run() {
            Runnable task = null;
            // 如果是新的任务，那么将当前传递过来的task对象的引用给到局部变量task
            if(isNewTask) {
                task = this.task;
            }
            // 是否编译
            boolean success = true;
            try {
                // 如果没有新的任务，则直接调用getTask()从workQueue中获取任务
                while (null != task || null != (task = getTask())) {
                    try {
                        // 执行当前任务
                        task.run();
                    } catch (Exception e) {
                    	// 如果任务执行发生异常，设置标志位为false
                        success = false;
                        logger.error("the task was executed with wrong, error stack is {}", e.getStackTrace());
                    } finally {
                        // 任务结束记得将局部变量task置为null，等待GC回收
                        task = null;
                        // 剔除执行完成的线程，并获取剩余的任务量
                        int restTaskNum = totalTask.decrementAndGet();
                        if(restTaskNum == 0) {
                            // 如果当前任务列表中的任务为0，则通知线程池shutdown
                            synchronized (shutDownNotify) {
                                shutDownNotify.notify();
                            }
                        }
                    }

                }
            } finally {
                // 当线程行完任务之后释放线程资源
                boolean remove = workers.remove(this);
                logger.info("current thread is removed, the rest number of workers is {}", workers.size());
                if(!success) {
                	// 可以根据需求拓展一些操作
                }
                tryClose(true);
            }
        }
    }

    //------------------------------------------------------------------------------------------
	public int getcoreTheadSize() {
		return coreTheadSize;
	}

	public void setcoreTheadSize(int coreTheadSize) {
		this.coreTheadSize = coreTheadSize;
	}

	public int getMaxSize() {
		return maxSize;
	}


	public void setMaxSize(int maxSize) {
	    this.maxSize = maxSize;
	}

	public long getKeepAliveTime() {
		return keepAliveTime;
	}

	public void setKeepAliveTime(long keepAliveTime) {
		this.keepAliveTime = keepAliveTime;
	}

	public TimeUnit getUnit() {
		return unit;
	}

	public void setUnit(TimeUnit unit) {
		this.unit = unit;
	}

	public BlockingQueue<Runnable> getWorkQueue() {
		return workQueue;
	}

	public void setWorkQueue(BlockingQueue<Runnable> workQueue) {
		this.workQueue = workQueue;
	}

	public Set<Worker> getWorkers() {
		return workers;
	}

	public void setWorkers(Set<Worker> workers) {
		this.workers = workers;
	}

}
