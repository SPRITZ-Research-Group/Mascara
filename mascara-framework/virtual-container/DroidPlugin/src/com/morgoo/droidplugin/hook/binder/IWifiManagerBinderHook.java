/*
**        DroidPlugin Project
**
** Copyright(c) 2015 Andy Zhang <zhangyong232@gmail.com>
**
** This file is part of DroidPlugin.
**
** DroidPlugin is free software: you can redistribute it and/or
** modify it under the terms of the GNU Lesser General Public
** License as published by the Free Software Foundation, either
** version 3 of the License, or (at your option) any later version.
**
** DroidPlugin is distributed in the hope that it will be useful,
** but WITHOUT ANY WARRANTY; without even the implied warranty of
** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
** Lesser General Public License for more details.
**
** You should have received a copy of the GNU Lesser General Public
** License along with DroidPlugin.  If not, see <http://www.gnu.org/licenses/lgpl.txt>
**
**/

package com.morgoo.droidplugin.hook.binder;

import android.content.Context;
import android.os.IBinder;

import com.morgoo.droidplugin.hook.BaseHookHandle;
import com.morgoo.droidplugin.hook.handle.IWifiManagerHookHandle;
import com.morgoo.droidplugin.reflect.FieldUtils;
import com.morgoo.helper.Log;
import com.morgoo.helper.compat.IWifiManagerCompat;
import com.morgoo.helper.compat.ServiceManagerCompat;

/**
 * Created by Andy Zhang(zhangyong232@gmail.com) on 2015/6/1.
 */
public class IWifiManagerBinderHook extends BinderHook {


    private final static String SERVICE_NAME = "wifi";
    private static final String TAG = IWifiManagerBinderHook.class.getSimpleName();

    public IWifiManagerBinderHook(Context hostContext) {
        super(hostContext);
        System.out.println("IWifiManagerBinderHook:IWifiManagerBinderHook");
    }

    @Override
    public Object getOldObj() throws Exception {
        System.out.println("IWifiManagerBinderHook:getOldObj");
        IBinder iBinder = MyServiceManager.getOriginService(SERVICE_NAME);
        return IWifiManagerCompat.asInterface(iBinder);
    }

    @Override
    public String getServiceName() {
        System.out.println("IWifiManagerBinderHook:getServiceName");
        return SERVICE_NAME;
    }

    @Override
    protected BaseHookHandle createHookHandle() {
        System.out.println("IWifiManagerBinderHook:createHookHandle");
        return new IWifiManagerHookHandle(mHostContext);
    }

    @Override
    protected void onInstall(ClassLoader classLoader) throws Throwable {
        System.out.println("IWifiManagerBinderHook:onInstall");
        super.onInstall(classLoader);
        fixZTESecurity();
    }

    /**适配ZTE S2005机型ZTESecurity*/
    private void fixZTESecurity() {
        System.out.println("IWifiManagerBinderHook:fixZTESecurity");
        try {
            Object proxyServiceIBinder =  MyServiceManager.getProxiedObj(getServiceName());
            IBinder serviceIBinder = ServiceManagerCompat.getService(getServiceName());
            if (serviceIBinder != null && proxyServiceIBinder != null && "com.zte.ZTESecurity.ZTEWifiService".equals(serviceIBinder.getClass().getName())) {
                Object obj = FieldUtils.readField(serviceIBinder, "mIWifiManager");
                setOldObj(obj);
                FieldUtils.writeField(serviceIBinder, "mIWifiManager", proxyServiceIBinder);
            }
        } catch (Exception e) {
            Log.i(TAG, "fixZTESecurity FAIL", e);
        }
    }
}
