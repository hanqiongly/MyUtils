package com.jack.executor.task;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.jack.executor.Callback;
import com.jack.executor.manager.PriorityRunnable;
import com.jack.executor.utils.LogUtil;

/**
 * Created by liuyang on 2017/1/11.
 */
public class TaskProxyImpl<ResultType> implements TaskProxy<ResultType> {

    static boolean isDebug = true;

    private static final InternalHandler sHandler = new InternalHandler();

    private final BaseTask<ResultType> task;
    private volatile boolean callOnCanceled = false;
    private volatile boolean callOnFinished = false;

    public TaskProxyImpl(BaseTask<ResultType> task) {
        this.task = task;
        this.task.setTaskProxy(this);
    }

    @Override
    public Runnable getRunnable() {
        this.onWaiting();
        PriorityRunnable runnable = new PriorityRunnable(
                task.getPriority(),
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // 等待过程中取消
                            if (callOnCanceled || isCancelled()) {
                                throw new Callback.CancelledException("");
                            }

                            // start running
                            onStarted();

                            if (isCancelled()) { // 开始时取消
                                throw new Callback.CancelledException("");
                            }

                            // 执行task, 得到结果.
                            task.setResult(task.doBackground());

                            // 未在doBackground过程中取消成功
                            if (isCancelled()) {
                                throw new Callback.CancelledException("");
                            }

                            // 执行成功
                            onSuccess(task.getResult());
                        } catch (Callback.CancelledException cex) {
                            onCancelled(cex);
                        } catch (Throwable ex) {
                            onError(ex, false);
                        } finally {
                            onFinished();
                        }
                    }
                });
        return runnable;
    }

    @Override
    public void onWaiting() {
        this.setState(BaseTask.State.WAITING);
        sHandler.obtainMessage(MSG_WHAT_ON_WAITING, this).sendToTarget();
    }

    @Override
    public void onStarted() {
        this.setState(BaseTask.State.STARTED);
        sHandler.obtainMessage(MSG_WHAT_ON_START, this).sendToTarget();
    }

    @Override
    public void onSuccess(ResultType result) {
        this.setState(BaseTask.State.SUCCESS);
        sHandler.obtainMessage(MSG_WHAT_ON_SUCCESS, this).sendToTarget();
    }

    @Override
    public void onError(Throwable ex, boolean isCallbackError) {
        this.setState(BaseTask.State.ERROR);
        sHandler.obtainMessage(MSG_WHAT_ON_ERROR, new ArgsObj(this, ex)).sendToTarget();
    }

    @Override
    public void onUpdate(int flag, Object... args) {
        sHandler.obtainMessage(MSG_WHAT_ON_UPDATE, flag, flag, new ArgsObj(this, args)).sendToTarget();
    }

    @Override
    public void onCancelled(Callback.CancelledException cex) {
        this.setState(BaseTask.State.CANCELLED);
        sHandler.obtainMessage(MSG_WHAT_ON_CANCEL, new ArgsObj(this, cex)).sendToTarget();
    }

    @Override
    public void onFinished() {
        sHandler.obtainMessage(MSG_WHAT_ON_FINISHED, this).sendToTarget();
    }

    @Override
    public final void setState(BaseTask.State state) {
        this.task.setState(state);
    }

    @Override
    public final Priority getPriority() {
        return task.getPriority();
    }

    @Override
    public void cancel() {
        task.cancel();
    }

    public static void runOnUi(Runnable runnable) {
        sHandler.post(runnable);
    }

    public static void runOnUiDelayed(Runnable runnable,long milliseconds) {
        sHandler.postDelayed(runnable, milliseconds);
    }

    public static void removeCallbacks(Runnable runnable) {
        sHandler.removeCallbacks(runnable);
    }

    @Override
    public boolean isCancelled() {
        return task.isCancelled();
    }

    private static class ArgsObj {
        final TaskProxyImpl taskProxy;
        final Object[] args;

        public ArgsObj(TaskProxyImpl taskProxy, Object... args) {
            this.taskProxy = taskProxy;
            this.args = args;
        }
    }

    private final static int MSG_WHAT_BASE = 1000000000;
    private final static int MSG_WHAT_ON_WAITING = MSG_WHAT_BASE + 1;
    private final static int MSG_WHAT_ON_START = MSG_WHAT_BASE + 2;
    private final static int MSG_WHAT_ON_SUCCESS = MSG_WHAT_BASE + 3;
    private final static int MSG_WHAT_ON_ERROR = MSG_WHAT_BASE + 4;
    private final static int MSG_WHAT_ON_UPDATE = MSG_WHAT_BASE + 5;
    private final static int MSG_WHAT_ON_CANCEL = MSG_WHAT_BASE + 6;
    private final static int MSG_WHAT_ON_FINISHED = MSG_WHAT_BASE + 7;

    public final static class InternalHandler extends Handler {

        private InternalHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        @SuppressWarnings("unchecked")
        public void handleMessage(Message msg) {
            if (msg.obj == null) {
                throw new IllegalArgumentException("msg must not be null");
            }
            TaskProxyImpl taskProxy = null;
            Object[] args = null;
            if (msg.obj instanceof TaskProxyImpl) {
                taskProxy = (TaskProxyImpl) msg.obj;
            } else if (msg.obj instanceof ArgsObj) {
                ArgsObj argsObj = (ArgsObj) msg.obj;
                taskProxy = argsObj.taskProxy;
                args = argsObj.args;
            }
            if (taskProxy == null) {
                throw new RuntimeException("msg.obj not instanceof TaskProxy");
            }

            try {
                switch (msg.what) {
                    case MSG_WHAT_ON_WAITING: {
                        taskProxy.task.onWaiting();
                        break;
                    }
                    case MSG_WHAT_ON_START: {
                        taskProxy.task.onStarted();
                        break;
                    }
                    case MSG_WHAT_ON_SUCCESS: {
                        taskProxy.task.onSuccess(taskProxy.task.getResult());
                        break;
                    }
                    case MSG_WHAT_ON_ERROR: {
                        assert args != null;
                        Throwable throwable = (Throwable) args[0];
                        LogUtil.d(throwable.getMessage(), throwable);
                        taskProxy.task.onError(throwable, false);
                        break;
                    }
                    case MSG_WHAT_ON_UPDATE: {
                        taskProxy.task.onUpdate(msg.arg1, args);
                        break;
                    }
                    case MSG_WHAT_ON_CANCEL: {
                        if (taskProxy.callOnCanceled) return;
                        taskProxy.callOnCanceled = true;
                        assert args != null;
                        taskProxy.task.onCancelled((com.jack.executor.Callback.CancelledException) args[0]);
                        break;
                    }
                    case MSG_WHAT_ON_FINISHED: {
                        if (taskProxy.callOnFinished) return;
                        taskProxy.callOnFinished = true;
                        taskProxy.task.onFinished();
                        break;
                    }
                    default: {
                        break;
                    }
                }
            } catch (Throwable ex) {
                taskProxy.setState(BaseTask.State.ERROR);
                if (msg.what != MSG_WHAT_ON_ERROR) {
                    taskProxy.task.onError(ex, true);
                } else if (isDebug/*x.isDebug()*/) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }
}
