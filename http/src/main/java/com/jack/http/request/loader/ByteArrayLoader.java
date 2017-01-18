package com.jack.http.request.loader;

import com.jack.cache.file.entity.DiskCacheEntity;
import com.jack.executor.utils.IOUtil;
import com.jack.http.request.UriRequest;

import java.io.InputStream;

/**
 * Author: wyouflf
 * Time: 2014/05/30
 */
public class ByteArrayLoader extends Loader<byte[]> {

    @Override
    public Loader<byte[]> newInstance() {
        return new ByteArrayLoader();
    }

    @Override
    public byte[] load(final InputStream in) throws Throwable {
        return IOUtil.readBytes(in);
    }

    @Override
    public byte[] load(final UriRequest request) throws Throwable {
        request.sendRequest();
        return this.load(request.getInputStream());
    }

    @Override
    public byte[] loadFromCache(final DiskCacheEntity cacheEntity) throws Throwable {
        return null;
    }

    @Override
    public void save2Cache(final UriRequest request) {
    }
}
