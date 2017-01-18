package com.jack.http.manage;

import android.content.Context;

import com.jack.executor.Callback;
import com.jack.executor.manager.TaskManager;
import com.jack.executor.manager.TaskManagerImpl;
import com.jack.http.HttpManager;
import com.jack.http.request.HttpMethod;
import com.jack.http.request.param.RequestParams;
import com.jack.http.task.HttpTask;

import java.lang.reflect.Type;

/**
 * HttpManager实现
 */
public final class HttpManagerImpl implements HttpManager {

    private static final Object lock = new Object();
    private static volatile HttpManagerImpl instance;
    private static Context mContext;

    public static Context getContext() {
        return mContext;
    }

    public static void setContext(Context context) {
        if (mContext == null)
        mContext = context;
    }

    private HttpManagerImpl(Context context) {
        if (mContext == null)
        mContext = context;

        if (taskManager == null) {
            taskManager = TaskManagerImpl.getInstance();
        }
    }

    public static HttpManagerImpl getInstance(Context context) {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new HttpManagerImpl(context);
                }
            }
        }
        return instance;
    }

    TaskManager taskManager;

    public void startAsyncAction(Runnable runnable) {

    }

    @Override
    public <T> Callback.Cancelable get(RequestParams entity, Callback.CommonCallback<T> callback) {
        return request(HttpMethod.GET, entity, callback);
    }

    @Override
    public <T> Callback.Cancelable post(RequestParams entity, Callback.CommonCallback<T> callback) {
        return request(HttpMethod.POST, entity, callback);
    }

    @Override
    public <T> Callback.Cancelable request(HttpMethod method, RequestParams entity, Callback.CommonCallback<T> callback) {
        entity.setMethod(method);
        Callback.Cancelable cancelable = null;
        if (callback instanceof Callback.Cancelable) {
            cancelable = (Callback.Cancelable) callback;
        }
        HttpTask<T> task = new HttpTask<T>(entity, cancelable, callback);
        return taskManager.start(task);
    }

    @Override
    public <T> T getSync(RequestParams entity, Class<T> resultType) throws Throwable {
        return requestSync(HttpMethod.GET, entity, resultType);
    }

    @Override
    public <T> T postSync(RequestParams entity, Class<T> resultType) throws Throwable {
        return requestSync(HttpMethod.POST, entity, resultType);
    }

    @Override
    public <T> T requestSync(HttpMethod method, RequestParams entity, Class<T> resultType) throws Throwable {
        DefaultSyncCallback<T> callback = new DefaultSyncCallback<T>(resultType);
        return requestSync(method, entity, callback);
    }

    @Override
    public <T> T requestSync(HttpMethod method, RequestParams entity, Callback.TypedCallback<T> callback) throws Throwable {
        entity.setMethod(method);
        HttpTask<T> task = new HttpTask<T>(entity, null, callback);
        return taskManager.startSync(task);
    }

    private class DefaultSyncCallback<T> implements Callback.TypedCallback<T> {

        private final Class<T> resultType;

        public DefaultSyncCallback(Class<T> resultType) {
            this.resultType = resultType;
        }

        @Override
        public Type getLoadType() {
            return resultType;
        }

        @Override
        public void onSuccess(T result) {

        }

        @Override
        public void onError(Throwable ex, boolean isOnCallback) {

        }

        @Override
        public void onCancelled(CancelledException cex) {

        }

        @Override
        public void onFinished() {

        }

        @Override
        public void onUpdate(int flag, Object... args) {

        }
    }
}
