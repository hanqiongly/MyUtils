package com.jack.executor.task;

import com.jack.executor.Callback;
import com.jack.executor.manager.TaskManagerImpl;

/**
 * @param <ResultType>
 */
public abstract class BaseTask<ResultType> implements Task<ResultType> {

    private TaskProxy taskProxy = null;
    private final Callback.Cancelable cancelHandler;

    protected Callback.CacheCallback<ResultType> cacheCallback;
    protected Callback.PrepareCallback prepareCallback;
    protected Callback.ProgressCallback progressCallback;

    protected volatile boolean hasException = false;
    protected final Callback.CommonCallback<ResultType> callback;

    private volatile boolean isCancelled = false;
    private volatile Task.State state = State.IDLE;
    private ResultType result;

    protected Object rawResult = null;
    protected volatile Boolean trustCache = null;
    protected final Object cacheLock = new Object();

    protected static final int FLAG_REQUEST_CREATED = 1;
    protected static final int FLAG_CACHE = 2;
    protected static final int FLAG_PROGRESS = 3;

    public BaseTask(Callback.Cancelable cancelHandler,CommonCallback<ResultType> commonCallback) {
        this.cancelHandler = cancelHandler;
        callback = commonCallback;

        if (callback instanceof Callback.CacheCallback) {
            this.cacheCallback = (Callback.CacheCallback<ResultType>) callback;
        }
        if (callback instanceof Callback.PrepareCallback) {
            this.prepareCallback = (Callback.PrepareCallback) callback;
        }
        if (callback instanceof Callback.ProgressCallback) {
            this.progressCallback = (Callback.ProgressCallback<ResultType>) callback;
        }
    }

    public void onWaiting() {
        if (progressCallback != null) {
            progressCallback.onWaiting();
        }
    }

    @Override
    public void onStarted() {
        if (progressCallback != null) {
            progressCallback.onStarted();
        }
    }

    @Override
    public void onSuccess(ResultType result) {
        if (hasException) return;
        callback.onSuccess(result);
    }

    public void onUpdate(int flag, Object... args) {
        switch (flag) {
            case FLAG_CACHE: {
                synchronized (cacheLock) {
                    try {
                        ResultType result = (ResultType) args[0];
                        trustCache = this.cacheCallback.onCache(result);
                    } catch (Throwable ex) {
                        trustCache = false;
                        callback.onError(ex, true);
                    } finally {
                        cacheLock.notifyAll();
                    }
                }
                break;
            }
            case FLAG_PROGRESS: {
                if (this.progressCallback != null && args.length == 3) {
                    try {
                        this.progressCallback.onLoading(
                                ((Number) args[0]).longValue(),
                                ((Number) args[1]).longValue(),
                                (Boolean) args[2]);
                    } catch (Throwable ex) {
                        callback.onError(ex, true);
                    }
                }
                break;
            }
            default: {
                break;
            }
        }
    }

    @Override
    public void onError(Throwable ex, boolean isCallbackError) {
        callback.onError(ex, isCallbackError);
    }


    @Override
    public void onCancelled(Callback.CancelledException cex) {
        callback.onCancelled(cex);
    }

    protected abstract void closeRequestSync() ;

    @Override
    public void onFinished() {
        cancelWorks();
        callback.onFinished();
    }



    @Override
    public void cancelWorks() {
        TaskManagerImpl.getInstance().run(new Runnable() {
            @Override
            public void run() {
                closeRequestSync();
            }
        });
    }



    public abstract Priority getPriority() ;

    public final void update(int flag, Object... args) {
        if (taskProxy != null) {
            taskProxy.onUpdate(flag, args);
        }
    }


    /**
     * 取消任务时是否不等待任务彻底结束, 立即收到取消的通知.
     */
    public boolean isCancelFast() {
        return false;
    }

    @Override
    public final synchronized void cancel() {
        if (!this.isCancelled) {
            this.isCancelled = true;
            cancelWorks();
            if (cancelHandler != null && !cancelHandler.isCancelled()) {
                cancelHandler.cancel();
            }
            if (this.state == State.WAITING || (this.state == State.STARTED && isCancelFast())) {
                if (taskProxy != null) {
                    taskProxy.onCancelled(new Callback.CancelledException("cancelled by user"));
                    taskProxy.onFinished();
                }
            }
        }
    }

    @Override
    public final boolean isCancelled() {
        return isCancelled || state == State.CANCELLED ||
                (cancelHandler != null && cancelHandler.isCancelled());
    }

    public final boolean isFinished() {
        return this.state.value() > State.STARTED.value();
    }

    public final Task.State getState() {
        return state;
    }

    public final ResultType getResult() {
        return result;
    }

    public void setState(State state) {
        this.state = state;
    }

    public final void setTaskProxy(TaskProxy taskProxy) {
        this.taskProxy = taskProxy;
    }

    public final void setResult(ResultType result) {
        this.result = result;
    }

}
