package com.jack.http.request.loader;

import com.jack.cache.file.entity.DiskCacheEntity;
import com.jack.http.request.UriRequest;

import java.io.InputStream;

/**
 * @author: wyouflf
 * @date: 2014/10/17
 */
public class IntegerLoader extends Loader<Integer> {
    @Override
    public Loader<Integer> newInstance() {
        return new IntegerLoader();
    }

    @Override
    public Integer load(InputStream in) throws Throwable {
        return 100;
    }

    @Override
    public Integer load(UriRequest request) throws Throwable {
        request.sendRequest();
        return request.getResponseCode();
    }

    @Override
    public Integer loadFromCache(final DiskCacheEntity cacheEntity) throws Throwable {
        return null;
    }

    @Override
    public void save2Cache(UriRequest request) {

    }
}
