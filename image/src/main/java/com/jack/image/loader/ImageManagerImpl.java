package com.jack.image.loader;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import com.jack.executor.Callback;
import com.jack.executor.manager.TaskManager;
import com.jack.executor.manager.TaskManagerImpl;
import com.jack.image.ImageManager;
import com.jack.image.decoder.ImageDecoder;

import java.io.File;

/**
 * Created by wyouflf on 15/10/9.
 */
public final class ImageManagerImpl implements ImageManager {
    TaskManager taskManager;

    private static final Object lock = new Object();
    private static volatile ImageManagerImpl instance;
    private static Context mContext;

    private ImageManagerImpl(Context context) {
        if (mContext == null)
        mContext = context;
        if (taskManager == null)
        taskManager = TaskManagerImpl.getInstance();
    }

    public static void setContext(Context context) {
        if (mContext == null)
        mContext = context;
    }

    public static Context getContext() {
        return mContext;
    }

    public static ImageManagerImpl getInstance(Context context) {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new ImageManagerImpl(context);
                }
            }
        }

        return instance;
    }

    @Override
    public void postDelayed(Runnable runnable,long delayMillis) {
        taskManager.postDelayed(runnable,delayMillis);
    }

    @Override
    public void autoPost(Runnable runnable) {
        taskManager.autoPost(runnable);
    }

    @Override
    public void startAsyncAction(Runnable runnable) {

    }

    @Override
    public void bind(final ImageView view, final String url) {
        taskManager.autoPost(new Runnable() {
            @Override
            public void run() {
                ImageLoader.doBind(view, url, null, null);
            }
        });
    }

    @Override
    public void bind(final ImageView view, final String url, final ImageOptions options) {
        taskManager.autoPost(new Runnable() {
            @Override
            public void run() {
                ImageLoader.doBind(view, url, options, null);
            }
        });
    }

    @Override
    public void bind(final ImageView view, final String url, final Callback.CommonCallback<Drawable> callback) {
        taskManager.autoPost(new Runnable() {
            @Override
            public void run() {
                ImageLoader.doBind(view, url, null, callback);
            }
        });
    }

    @Override
    public void bind(final ImageView view, final String url, final ImageOptions options, final Callback.CommonCallback<Drawable> callback) {
        taskManager.autoPost(new Runnable() {
            @Override
            public void run() {
                ImageLoader.doBind(view, url, options, callback);
            }
        });
    }

    @Override
    public Callback.Cancelable loadDrawable(String url, ImageOptions options, Callback.CommonCallback<Drawable> callback) {
        return ImageLoader.doLoadDrawable(url, options, callback);
    }

    @Override
    public Callback.Cancelable loadFile(String url, ImageOptions options, Callback.CacheCallback<File> callback) {
        return ImageLoader.doLoadFile(url, options, callback);
    }

    @Override
    public void clearMemCache() {
        ImageLoader.clearMemCache();
    }

    @Override
    public void clearCacheFiles() {
        ImageLoader.clearCacheFiles();
        ImageDecoder.clearCacheFiles();
    }
}
