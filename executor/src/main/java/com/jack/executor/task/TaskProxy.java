package com.jack.executor.task;

import com.jack.executor.Callback;

/**
 * Created by liuyang on 2017/1/11.
 */
public interface TaskProxy<ResultType> extends Callback.Cancelable{

    public Runnable getRunnable();

    public void onWaiting() ;

    public void onStarted() ;

    public void onSuccess(ResultType result) ;

    public void onError(Throwable ex, boolean isCallbackError) ;

    public void onUpdate(int flag, Object... args) ;

    public void onCancelled(Callback.CancelledException cex) ;

    public void onFinished() ;

    public  void setState(BaseTask.State state) ;

    public Priority getPriority() ;


}
