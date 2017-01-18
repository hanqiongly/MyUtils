package com.jack.http.request.loader;

import android.text.TextUtils;

import com.jack.cache.file.entity.DiskCacheEntity;
import com.jack.executor.utils.IOUtil;
import com.jack.http.annotation.HttpResponse;
import com.jack.http.request.UriRequest;
import com.jack.http.request.param.RequestParams;
import com.jack.http.response.InputStreamResponseParser;
import com.jack.http.response.ResponseParser;
import com.jack.http.task.ParameterizedTypeUtil;

import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.List;

/**
 * Created by lei.jiao on 2014/6/27.
 * 其他对象的下载转换.
 * 使用类型上的@HttpResponse注解信息进行数据转换.
 */
public class ObjectLoader extends Loader<Object> {

    private String charset = "UTF-8";
    private String resultStr = null;

    private final Type objectType;
    private final Class<?> objectClass;
    private final ResponseParser parser;

    public ObjectLoader(Type objectType) {
        this.objectType = objectType;

        // check loadType & resultType
        {
            if (objectType instanceof ParameterizedType) {
                objectClass = (Class<?>) ((ParameterizedType) objectType).getRawType();
            } else if (objectType instanceof TypeVariable) {
                throw new IllegalArgumentException(
                        "not support callback type " + objectType.toString());
            } else {
                objectClass = (Class<?>) objectType;
            }
        }

        if (List.class.equals(objectClass)) {
            Type itemType = ParameterizedTypeUtil.getParameterizedType(this.objectType, List.class, 0);
            Class<?> itemClass = null;
            if (itemType instanceof ParameterizedType) {
                itemClass = (Class<?>) ((ParameterizedType) itemType).getRawType();
            } else if (itemType instanceof TypeVariable) {
                throw new IllegalArgumentException(
                        "not support callback type " + itemType.toString());
            } else {
                itemClass = (Class<?>) itemType;
            }

            HttpResponse response = itemClass.getAnnotation(HttpResponse.class);
            if (response != null) {
                try {
                    this.parser = response.parser().newInstance();
                } catch (Throwable ex) {
                    throw new RuntimeException("create parser error", ex);
                }
            } else {
                throw new IllegalArgumentException("not found @HttpResponse from " + itemType);
            }
        } else {
            HttpResponse response = objectClass.getAnnotation(HttpResponse.class);
            if (response != null) {
                try {
                    this.parser = response.parser().newInstance();
                } catch (Throwable ex) {
                    throw new RuntimeException("create parser error", ex);
                }
            } else {
                throw new IllegalArgumentException("not found @HttpResponse from " + this.objectType);
            }
        }
    }

    @Override
    public Loader<Object> newInstance() {
        throw new IllegalAccessError("use constructor create ObjectLoader.");
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
    public Object load(final InputStream in) throws Throwable {
        Object result;
        if (parser instanceof InputStreamResponseParser) {
            result = ((InputStreamResponseParser) parser).parse(objectType, objectClass, in);
        } else {
            resultStr = IOUtil.readStr(in, charset);
            result = parser.parse(objectType, objectClass, resultStr);
        }
        return result;
    }

    @Override
    public Object load(final UriRequest request) throws Throwable {
        try {
            request.sendRequest();
        } finally {
            parser.checkResponse(request);
        }
        return this.load(request.getInputStream());
    }

    @Override
    public Object loadFromCache(final DiskCacheEntity cacheEntity) throws Throwable {
        if (cacheEntity != null) {
            String text = cacheEntity.getTextContent();
            if (!TextUtils.isEmpty(text)) {
                return parser.parse(objectType, objectClass, text);
            }
        }

        return null;
    }

    @Override
    public void save2Cache(UriRequest request) {
        saveStringCache(request, resultStr);
    }
}