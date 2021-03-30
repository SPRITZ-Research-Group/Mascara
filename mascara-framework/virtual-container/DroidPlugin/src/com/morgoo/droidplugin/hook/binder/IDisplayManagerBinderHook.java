package com.morgoo.droidplugin.hook.binder;

import android.content.Context;
import android.os.IBinder;

import com.morgoo.droidplugin.hook.BaseHookHandle;
import com.morgoo.droidplugin.hook.handle.IDisplayManagerHookHandle;
import com.morgoo.droidplugin.reflect.FieldUtils;
import com.morgoo.droidplugin.reflect.MethodUtils;
import com.morgoo.helper.compat.IDisplayManagerCompat;

/**
 * IDisplayManagerBinderHook
 *
 * @author Liu Yichen
 * @date 16/6/13
 */
public class IDisplayManagerBinderHook extends BinderHook {

    private static final String TAG = IDisplayManagerBinderHook.class.getSimpleName();
    private static final String SERVICE_NAME = Context.DISPLAY_SERVICE;

    public IDisplayManagerBinderHook(Context hostContext) {
        super(hostContext);
        System.out.println("IDisplayManagerBinderHook:IDisplayManagerBinderHook");
    }

    @Override
    public String getServiceName() {
        System.out.println("IDisplayManagerBinderHook:getServiceName");
        return SERVICE_NAME;
    }

    @Override
    Object getOldObj() throws Exception {
        System.out.println("IDisplayManagerBinderHook:getOldObj");
        IBinder iBinder = MyServiceManager.getOriginService(SERVICE_NAME);
        return IDisplayManagerCompat.asInterface(iBinder);
    }

    @Override
    protected void onInstall(ClassLoader classLoader) throws Throwable {
        System.out.println("IDisplayManagerBinderHook:onInstall");
        super.onInstall(classLoader);
        Class displayManagerGlobalClass = Class.forName("android.hardware.display.DisplayManagerGlobal");
        Object displayManagerGlobal = MethodUtils.invokeStaticMethod(displayManagerGlobalClass, "getInstance");
        FieldUtils.writeField(displayManagerGlobal, "mDm", MyServiceManager.getProxiedObj(SERVICE_NAME));
    }

    @Override
    protected BaseHookHandle createHookHandle() {
        System.out.println("IDisplayManagerBinderHook:createHookHandle");
        return new IDisplayManagerHookHandle(mHostContext);
    }

}
