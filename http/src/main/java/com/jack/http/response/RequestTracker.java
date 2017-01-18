package com.jack.http.response;

import com.jack.http.request.UriRequest;
import com.jack.http.request.param.RequestParams;

/**
 * Created by wyouflf on 15/9/10.
 * 请求过程追踪, 适合用来记录请求日志.
 * 所有回调方法都在主线程进行.
 * <p>
 * 用法:
 * 1. 将RequestTracker实例设置给请求参数RequestParams.
 * 2. 请的callback参数同时实现RequestTracker接口;
 * 3. 注册给UriRequestFactory的默认RequestTracker.
 * 注意: 请求回调RequestTracker时优先级按照上面的顺序,
 * 找到一个RequestTracker的实现会忽略其他.
 */
public interface RequestTracker {

    public void onWaiting(RequestParams params);

    public void onStart(RequestParams params);

    public void onRequestCreated(UriRequest request);

    public void onCache(UriRequest request, Object result);

    public void onSuccess(UriRequest request, Object result);

    public void onCancelled(UriRequest request);

    public void onError(UriRequest request, Throwable ex, boolean isCallbackError);

    public void onFinished(UriRequest request);
}
