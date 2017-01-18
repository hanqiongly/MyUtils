package com.jack.http.task;

import android.text.TextUtils;

import com.jack.executor.Callback;
import com.jack.executor.task.BaseTask;
import com.jack.executor.task.Priority;
import com.jack.executor.utils.IOUtil;
import com.jack.executor.utils.LogUtil;
import com.jack.http.exception.HttpException;
import com.jack.http.exception.HttpRedirectException;
import com.jack.http.request.HttpMethod;
import com.jack.http.request.ProgressHandler;
import com.jack.http.request.UriRequest;
import com.jack.http.request.UriRequestFactory;
import com.jack.http.request.param.RequestParams;
import com.jack.http.response.HttpRetryHandler;
import com.jack.http.response.RedirectHandler;
import com.jack.http.response.RequestInterceptListener;

import java.io.Closeable;
import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpTask<ResultType> extends BaseTask<ResultType> implements ProgressHandler {

    // 请求相关
    private RequestParams params;
    private UriRequest request;
    private RequestWorker requestWorker;

    private RequestInterceptListener requestInterceptListener;

    // 文件下载线程数限制
    private Type loadType;
    private final static int MAX_FILE_LOAD_WORKER = 3;
    private final static AtomicInteger sCurrFileLoadCount = new AtomicInteger(0);

    // 文件下载任务
    private static final HashMap<String, WeakReference<HttpTask<?>>>
            DOWNLOAD_TASK = new HashMap<String, WeakReference<HttpTask<?>>>(1);


    public HttpTask(RequestParams params, Callback.Cancelable cancelHandler,
                    Callback.CommonCallback<ResultType> callback) {
        super(cancelHandler,callback);

        assert params != null;
        assert callback != null;
        this.params = params;
        if (callback instanceof RequestInterceptListener) {
            this.requestInterceptListener = (RequestInterceptListener) callback;
        }
    }

    // 解析loadType
    private void resolveLoadType() {
        Class<?> callBackType = callback.getClass();
        if (callback instanceof Callback.TypedCallback) {
            loadType = ((Callback.TypedCallback) callback).getLoadType();
        } else if (callback instanceof Callback.PrepareCallback) {
            loadType = ParameterizedTypeUtil.getParameterizedType(callBackType, Callback.PrepareCallback.class, 0);
        } else {
            loadType = ParameterizedTypeUtil.getParameterizedType(callBackType, Callback.CommonCallback.class, 0);
        }
    }

    // 初始化请求参数
    private UriRequest createNewRequest() throws Throwable {
        params.init();
        UriRequest result = UriRequestFactory.getUriRequest(params, loadType);
        result.setCallingClassLoader(callback.getClass().getClassLoader());
        result.setProgressHandler(this);
        this.loadingUpdateMaxTimeSpan = params.getLoadingUpdateMaxTimeSpan();
        this.update(FLAG_REQUEST_CREATED, result);
        return result;
    }

    // 文件下载冲突检测
    private void checkDownloadTask() {
        if (File.class == loadType) {
            synchronized (DOWNLOAD_TASK) {
                String downloadTaskKey = this.params.getSaveFilePath();
                if (!TextUtils.isEmpty(downloadTaskKey)) {
                    WeakReference<HttpTask<?>> taskRef = DOWNLOAD_TASK.get(downloadTaskKey);
                    if (taskRef != null) {
                        HttpTask<?> task = taskRef.get();
                        if (task != null) {
                            task.cancel();
                            task.closeRequestSync();
                        }
                        DOWNLOAD_TASK.remove(downloadTaskKey);
                    }
                    DOWNLOAD_TASK.put(downloadTaskKey, new WeakReference<HttpTask<?>>(this));
                } // end if (!TextUtils.isEmpty(downloadTaskKey))

                if (DOWNLOAD_TASK.size() > MAX_FILE_LOAD_WORKER) {
                    Iterator<Map.Entry<String, WeakReference<HttpTask<?>>>>
                            entryItr = DOWNLOAD_TASK.entrySet().iterator();
                    while (entryItr.hasNext()) {
                        Map.Entry<String, WeakReference<HttpTask<?>>> next = entryItr.next();
                        WeakReference<HttpTask<?>> value = next.getValue();
                        if (value == null || value.get() == null) {
                            entryItr.remove();
                        }
                    }
                }
            } // end synchronized
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ResultType doBackground() throws Throwable {

        if (this.isCancelled()) {
            throw new Callback.CancelledException("cancelled before request");
        }

        ResultType result = null;
        resolveLoadType();
        request = createNewRequest();
        checkDownloadTask();
        boolean retry = true;
        int retryCount = 0;
        Throwable exception = null;
        HttpRetryHandler retryHandler = this.params.getHttpRetryHandler();
        if (retryHandler == null) {
            retryHandler = new HttpRetryHandler();
        }
        retryHandler.setMaxRetryCount(this.params.getMaxRetryCount());

        if (this.isCancelled()) {
            throw new Callback.CancelledException("cancelled before request");
        }

        // 检查缓存
        Object cacheResult = null;
        if (cacheCallback != null && HttpMethod.permitsCache(params.getMethod())) {
            try {
                clearRawResult();
                LogUtil.d("load cache: " + this.request.getRequestUri());
                rawResult = this.request.loadResultFromCache();
            } catch (Throwable ex) {
                LogUtil.w("load disk cache error", ex);
            }

            if (this.isCancelled()) {
                clearRawResult();
                throw new Callback.CancelledException("cancelled before request");
            }

            if (rawResult != null) {
                if (prepareCallback != null) {
                    try {
                        cacheResult = prepareCallback.prepare(rawResult);
                    } catch (Throwable ex) {
                        cacheResult = null;
                        LogUtil.w("prepare disk cache error", ex);
                    } finally {
                        clearRawResult();
                    }
                } else {
                    cacheResult = rawResult;
                }

                if (this.isCancelled()) {
                    throw new Callback.CancelledException("cancelled before request");
                }

                if (cacheResult != null) {
                    // 同步等待是否信任缓存
                    this.update(FLAG_CACHE, cacheResult);
                    while (trustCache == null) {
                        synchronized (cacheLock) {
                            try {
                                cacheLock.wait();
                            } catch (InterruptedException iex) {
                                throw new Callback.CancelledException("cancelled before request");
                            } catch (Throwable ignored) {
                            }
                        }
                    }

                    if (trustCache) {
                        return null;
                    }
                }
            }
        }

        if (trustCache == null) {
            trustCache = false;
        }

        if (cacheResult == null) {
            this.request.clearCacheHeader();
        }

        // 判断请求的缓存策略
        if (callback instanceof Callback.ProxyCacheCallback) {
            if (((Callback.ProxyCacheCallback) callback).onlyCache()) {
                return null;
            }
        }

        // 发起请求
        retry = true;
        while (retry) {
            retry = false;

            try {
                if (this.isCancelled()) {
                    throw new Callback.CancelledException("cancelled before request");
                }

                // 由loader发起请求, 拿到结果.
                this.request.close(); // retry 前关闭上次请求

                try {
                    clearRawResult();
                    // 开始请求工作
                    LogUtil.d("load: " + this.request.getRequestUri());
                    requestWorker = new RequestWorker();
                    requestWorker.request();
                    if (requestWorker.ex != null) {
                        throw requestWorker.ex;
                    }
                    rawResult = requestWorker.result;
                } catch (Throwable ex) {
                    clearRawResult();
                    if (this.isCancelled()) {
                        throw new Callback.CancelledException("cancelled during request");
                    } else {
                        throw ex;
                    }
                }

                if (prepareCallback != null) {

                    if (this.isCancelled()) {
                        throw new Callback.CancelledException("cancelled before request");
                    }

                    try {
                        result = (ResultType) prepareCallback.prepare(rawResult);
                    } finally {
                        clearRawResult();
                    }
                } else {
                    result = (ResultType) rawResult;
                }

                // 保存缓存
                if (cacheCallback != null && HttpMethod.permitsCache(params.getMethod())) {
                    this.request.save2Cache();
                }

                if (this.isCancelled()) {
                    throw new Callback.CancelledException("cancelled after request");
                }
            } catch (HttpRedirectException redirectEx) {
                retry = true;
                LogUtil.w("Http Redirect:" + params.getUri());
            } catch (Throwable ex) {
                switch (this.request.getResponseCode()) {
                    case 204: // empty content
                    case 205: // empty content
                    case 304: // disk cache is valid.
                        return null;
                    default: {
                        exception = ex;
                        if (this.isCancelled() && !(exception instanceof Callback.CancelledException)) {
                            exception = new Callback.CancelledException("canceled by user");
                        }
                        retry = retryHandler.canRetry(this.request, exception, ++retryCount);
                    }
                }
            }

        }

        if (exception != null && result == null && !trustCache) {
            hasException = true;
            throw exception;
        }

        return result;
    }

    @Override
    public boolean isCancelFast() {
        return params.isCancelFast();
    }
    private void clearRawResult() {
        if (rawResult instanceof Closeable) {
            IOUtil.closeQuietly((Closeable) rawResult);
        }
        rawResult = null;
    }
    protected void closeRequestSync() {
        clearRawResult();
        IOUtil.closeQuietly(request);
    }

    @Override
    public Priority getPriority() {
        return params.getPriority();
    }


    private long lastUpdateTime;
    private long loadingUpdateMaxTimeSpan = 300; // 300ms

    /**
     * @param total
     * @param current
     * @param forceUpdateUI
     * @return continue
     */
    @Override
    public boolean updateProgress(long total, long current, boolean forceUpdateUI) {

        if (isCancelled() || isFinished()) {
            return false;
        }

        if (progressCallback != null && request != null && total > 0) {
            if (total < current) {
                total = current;
            }
            if (forceUpdateUI) {
                lastUpdateTime = System.currentTimeMillis();
                this.update(FLAG_PROGRESS, total, current, request.isLoading());
            } else {
                long currTime = System.currentTimeMillis();
                if (currTime - lastUpdateTime >= loadingUpdateMaxTimeSpan) {
                    lastUpdateTime = currTime;
                    this.update(FLAG_PROGRESS, total, current, request.isLoading());
                }
            }
        }

        return !isCancelled() && !isFinished();
    }

    // ############################### end: region implements ProgressHandler

    @Override
    public String toString() {
        return params.toString();
    }


    /**
     * 请求发送和加载数据线程.
     * 该线程被join到HttpTask的工作线程去执行.
     * 它的主要作用是为了能强行中断请求的链接过程;
     * 并辅助限制同时下载文件的线程数.
     */
    private final class RequestWorker {
        /*private*/ Object result;
        /*private*/ Throwable ex;

        private RequestWorker() {
        }

        public void request() {
            try {
                boolean interrupted = false;
                if (File.class == loadType) {
                    while (sCurrFileLoadCount.get() >= MAX_FILE_LOAD_WORKER
                            && !HttpTask.this.isCancelled()) {
                        synchronized (sCurrFileLoadCount) {
                            try {
                                sCurrFileLoadCount.wait(10);
                            } catch (InterruptedException iex) {
                                interrupted = true;
                                break;
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                    sCurrFileLoadCount.incrementAndGet();
                }

                if (interrupted || HttpTask.this.isCancelled()) {
                    throw new Callback.CancelledException("cancelled before request" + (interrupted ? "(interrupted)" : ""));
                }

                try {
                    request.setRequestInterceptListener(requestInterceptListener);
                    this.result = request.loadResult();
                } catch (Throwable ex) {
                    this.ex = ex;
                }

                if (this.ex != null) {
                    throw this.ex;
                }
            } catch (Throwable ex) {
                this.ex = ex;
                if (ex instanceof HttpException) {
                    HttpException httpEx = (HttpException) ex;
                    int errorCode = httpEx.getCode();
                    if (errorCode == 301 || errorCode == 302) {
                        RedirectHandler redirectHandler = params.getRedirectHandler();
                        if (redirectHandler != null) {
                            try {
                                RequestParams redirectParams = redirectHandler.getRedirectParams(request);
                                if (redirectParams != null) {
                                    if (redirectParams.getMethod() == null) {
                                        redirectParams.setMethod(params.getMethod());
                                    }
                                    // 开始重定向请求
                                    HttpTask.this.params = redirectParams;
                                    HttpTask.this.request = createNewRequest();
                                    this.ex = new HttpRedirectException(errorCode, httpEx.getMessage(), httpEx.getResult());
                                }
                            } catch (Throwable throwable) {
                                this.ex = ex;
                            }
                        }
                    }
                }
            } finally {
                if (File.class == loadType) {
                    synchronized (sCurrFileLoadCount) {
                        sCurrFileLoadCount.decrementAndGet();
                        sCurrFileLoadCount.notifyAll();
                    }
                }
            }
        }
    }

}
