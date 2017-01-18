package com.jack.executor.task;

import com.jack.executor.Callback;

/**
 * Created by liuyang on 2017/1/12.
 */
interface Task<ResultType> extends Callback.Cancelable,Callback.CommonCallback<ResultType>{
    ResultType doBackground() throws Throwable;

    void onWaiting() ;

    void onStarted() ;

    void onFinished() ;

    Priority getPriority() ;

    void update(int flag, Object... args) ;

    void cancelWorks() ;

    boolean isCancelFast() ;

    boolean isFinished() ;

    State getState() ;

    ResultType getResult() ;

    void setState(State state) ;

    void setTaskProxy(TaskProxy taskProxy) ;

    void setResult(ResultType result) ;

    public enum State {
        IDLE(0), WAITING(1), STARTED(2), SUCCESS(3), CANCELLED(4), ERROR(5);
        private final int value;

        private State(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }
    }
}
