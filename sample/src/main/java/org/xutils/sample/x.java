package org.xutils.sample;

import android.app.Application;
import android.content.Context;

import com.jack.cache.DbManager;
import com.jack.cache.db.DbManagerImpl;
import com.jack.executor.manager.TaskManager;
import com.jack.executor.manager.TaskManagerImpl;
import com.jack.http.HttpManager;
import com.jack.http.manage.HttpManagerImpl;
import com.jack.image.ImageManager;
import com.jack.image.loader.ImageManagerImpl;
import com.jack.widget.ViewInjector;
import com.jack.widget.injector.ViewInjectorImpl;

import java.lang.reflect.Method;


/**
 * Created by wyouflf on 15/6/10.
 * 任务控制中心, http, image, db, view注入等接口的入口.
 * 需要在在application的onCreate中初始化: x.Ext.init(this);
 */
public final class x {

    private x() {
    }

    public static boolean isDebug() {
        return Ext.debug;
    }

    public static Application app() {
        if (Ext.app == null) {
            try {
                // 在IDE进行布局预览时使用
                Class<?> renderActionClass = Class.forName("com.android.layoutlib.bridge.impl.RenderAction");
                Method method = renderActionClass.getDeclaredMethod("getCurrentContext");
                Context context = (Context) method.invoke(null);
                Ext.app = new MockApplication(context);
            } catch (Throwable ignored) {
                throw new RuntimeException("please invoke x.Ext.init(app) on Application#onCreate()"
                        + " and register your Application in manifest.");
            }
        }
        return Ext.app;
    }

    public static TaskManager task() {
        return Ext.taskController;
    }

    public static HttpManager http() {
        if (Ext.httpManager == null) {
            Ext.setHttpManager(HttpManagerImpl.getInstance(Ext.app));
        }
        return Ext.httpManager;
    }

    public static ImageManager image() {
        if (Ext.imageManager == null) {
            Ext.setImageManager(ImageManagerImpl.getInstance(Ext.app));
        }
        return Ext.imageManager;
    }

    public static ViewInjector view() {
        if (Ext.viewInjector == null) {
            Ext.setViewInjector(ViewInjectorImpl.getInstance());
        }
        return Ext.viewInjector;
    }

    public static DbManager getDb(DbManager.DaoConfig daoConfig) {
        return DbManagerImpl.getInstance(daoConfig);
    }

    public static class Ext {
        private static boolean debug;
        private static Application app;
        private static TaskManager taskController;
        private static HttpManager httpManager;
        private static ImageManager imageManager;
        private static ViewInjector viewInjector;

        private Ext() {
        }

        public static void init(Application app) {
            Ext.setTaskController(TaskManagerImpl.getInstance());
            DbManagerImpl.setContext(app);
            Ext.setHttpManager(HttpManagerImpl.getInstance(app));
            Ext.setImageManager(ImageManagerImpl.getInstance(app));
            Ext.setViewInjector(ViewInjectorImpl.getInstance() );
            if (Ext.app == null) {
                Ext.app = app;
            }
        }

        public static void setDebug(boolean debug) {
            Ext.debug = debug;
        }

        public static void setTaskController(TaskManager taskController) {
            if (Ext.taskController == null) {
                Ext.taskController = taskController;
            }
        }

        public static void setHttpManager(HttpManager httpManager) {
            Ext.httpManager = httpManager;
        }

        public static void setImageManager(ImageManager imageManager) {
            Ext.imageManager = imageManager;
        }

        public static void setViewInjector(ViewInjector viewInjector) {
            Ext.viewInjector = viewInjector;
        }
    }

    private static class MockApplication extends Application {
        public MockApplication(Context baseContext) {
            this.attachBaseContext(baseContext);
        }
    }
}
