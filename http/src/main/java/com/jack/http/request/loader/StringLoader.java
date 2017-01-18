package com.jack.http.request.loader;

import android.text.TextUtils;

import com.jack.cache.file.entity.DiskCacheEntity;
import com.jack.executor.utils.IOUtil;
import com.jack.http.request.UriRequest;
import com.jack.http.request.param.RequestParams;

import java.io.InputStream;

/**
 * Author: wyouflf
 * Time: 2014/05/30
 */
public class StringLoader extends Loader<String> {

    private String charset = "UTF-8";
    private String resultStr = null;

    @Override
    public Loader<String> newInstance() {
        return new StringLoader();
    }

    @Override
    public void setParams(final RequestParams params) {
        if (params != null) {
            String charset = params.getCharset();
            if (!TextUtils.isEmpty(charset)) {
                this.charset = charset;
            }
        }
    }

    @Override
    public String load(final InputStream in) throws Throwable {
        resultStr = IOUtil.readStr(in, charset);
        return resultStr;
    }

    @Override
    public String load(final UriRequest request) throws Throwable {
        request.sendRequest();
        return this.load(request.getInputStream());
    }

    @Override
    public String loadFromCache(final DiskCacheEntity cacheEntity) throws Throwable {
        if (cacheEntity != null) {
            return cacheEntity.getTextContent();
        }

        return null;
    }

    @Override
    public void save2Cache(UriRequest request) {
        saveStringCache(request, resultStr);
    }
}
