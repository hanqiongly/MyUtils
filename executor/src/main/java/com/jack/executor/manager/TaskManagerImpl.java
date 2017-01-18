package com.jack.executor.manager;

import android.os.Looper;


import com.jack.executor.Callback;
import com.jack.executor.task.BaseTask;
import com.jack.executor.task.TaskProxy;
import com.jack.executor.task.TaskProxyImpl;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步任务的管理类
 */
public final class TaskManagerImpl implements TaskManager {
    private static final PriorityExecutor sDefaultExecutor = new PriorityExecutor(true);

    private TaskManagerImpl() {
    }

    private static volatile TaskManagerImpl instance;

    public static TaskManagerImpl getInstance() {
        if (instance == null) {
            synchronized (TaskManager.class) {
                if (instance == null) {
                    instance = new TaskManagerImpl();
                }
            }
        }

        return instance;
    }

    /**
     * run task
     *
     * @param task
     * @param <T>
     * @return
     */
    @Override
    public <T> TaskProxy<T> start(BaseTask<T> task) {
        TaskProxyImpl<T> proxy  = new TaskProxyImpl<T>(task);
            run(proxy.getRunnable());
        return proxy;
    }

    public <T> TaskProxy<T> start(TaskProxy<T> proxy) {
        run(proxy.getRunnable());
        return proxy;
    }

    @Override
    public <T> T startSync(BaseTask<T> task) throws Throwable {
        T result = null;
        try {
            task.onWaiting();
            task.onStarted();
            result = task.doBackground();
            task.onSuccess(result);
        } catch (Callback.CancelledException cex) {
            task.onCancelled(cex);
        } catch (Throwable ex) {
            task.onError(ex, false);
            throw ex;
        } finally {
            task.onFinished();
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends BaseTask<?>> Callback.Cancelable startTasks(
            final Callback.GroupCallback<T> groupCallback, final T... tasks) {

        if (tasks == null) {
            throw new IllegalArgumentException("task must not be null");
        }

        final Runnable callIfOnAllFinished = new Runnable() {
            private final int total = tasks.length;
            private final AtomicInteger count = new AtomicInteger(0);

            @Override
            public void run() {
                if (count.incrementAndGet() == total) {
                    if (groupCallback != null) {
                        groupCallback.onAllFinished();
                    }
                }
            }
        };

        for (final T task : tasks) {
            start(new TaskProxyImpl(task) {
                @Override
                public void onSuccess(Object result) {
                    super.onSuccess(result);
                    post(new Runnable() {
                        @Override
                        public void run() {
                            if (groupCallback != null) {
                                groupCallback.onSuccess(task);
                            }
                        }
                    });
                }

                @Override
                public void onCancelled(final Callback.CancelledException cex) {
                    super.onCancelled(cex);
                    post(new Runnable() {
                        @Override
                        public void run() {
                            if (groupCallback != null) {
                                groupCallback.onCancelled(task, cex);
                            }
                        }
                    });
                }

                @Override
                public void onError(final Throwable ex, final boolean isCallbackError) {
                    super.onError(ex, isCallbackError);
                    post(new Runnable() {
                        @Override
                        public void run() {
                            if (groupCallback != null) {
                                groupCallback.onError(task, ex, isCallbackError);
                            }
                        }
                    });
                }

                @Override
                public void onFinished() {
                    super.onFinished();
                    post(new Runnable() {
                        @Override
                        public void run() {
                            if (groupCallback != null) {
                                groupCallback.onFinished(task);
                            }
                            callIfOnAllFinished.run();
                        }
                    });
                }
            });
        }

        return new Callback.Cancelable() {

            @Override
            public void cancel() {
                for (T task : tasks) {
                    task.cancel();
                }
            }

            @Override
            public boolean isCancelled() {
                boolean isCancelled = true;
                for (T task : tasks) {
                    if (!task.isCancelled()) {
                        isCancelled = false;
                    }
                }
                return isCancelled;
            }
        };
    }

    @Override
    public void autoPost(Runnable runnable) {
        if (runnable == null) return;
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            runnable.run();
        } else {
            runOnUi(runnable);
        }
    }

    /**
     * run in UI thread
     *
     * @param runnable
     */
    @Override
    public void post(Runnable runnable) {
        if (runnable == null) return;
        runOnUi(runnable);
    }

    /**
     * run in UI thread
     *
     * @param runnable
     * @param delayMillis
     */
    @Override
    public void postDelayed(Runnable runnable, long delayMillis) {
        if (runnable == null) return;
        runOnUiDelayed(runnable, delayMillis);
    }

    /**
     * run in background thread
     *
     * @param runnable
     */
    @Override
    public void run(Runnable runnable) {
        if (!sDefaultExecutor.isBusy()) {
            sDefaultExecutor.execute(runnable);
        } else {
            new Thread(runnable).start();
        }
    }

    /**
     * 移除post或postDelayed提交的, 未执行的runnable
     *
     * @param runnable
     */
    @Override
    public void removeCallbacks(Runnable runnable) {
        TaskProxyImpl.removeCallbacks(runnable);
    }

    private void runOnUi(Runnable runnable) {
        TaskProxyImpl.runOnUi(runnable);
    }

    private void runOnUiDelayed(Runnable runnable,long delayMillis) {
        TaskProxyImpl.runOnUiDelayed(runnable, delayMillis);
    }
}
