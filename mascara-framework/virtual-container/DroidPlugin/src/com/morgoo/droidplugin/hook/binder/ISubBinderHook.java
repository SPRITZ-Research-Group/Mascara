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
import com.morgoo.droidplugin.hook.handle.ISubBinderHookHandle;
import com.morgoo.helper.compat.ISubCompat;
import com.morgoo.helper.compat.ITelephonyRegistryCompat;

/**
 * Created by Andy Zhang(zhangyong232@gmail.com) on 2016/5/6.
 */
public class ISubBinderHook extends BinderHook {


    private final static String SERVICE_NAME = "isub";

    public ISubBinderHook(Context hostContext) {
        super(hostContext);
        System.out.println("ISubBinderHook:ISubBinderHook");
    }

    @Override
    Object getOldObj() throws Exception {
        System.out.println("ISubBinderHook:getOldObj");
        IBinder iBinder = MyServiceManager.getOriginService(SERVICE_NAME);
        return ISubCompat.asInterface(iBinder);
    }

    @Override
    public String getServiceName() {
        System.out.println("ISubBinderHook:getServiceName");
        return SERVICE_NAME;
    }

    @Override
    protected BaseHookHandle createHookHandle() {
        System.out.println("ISubBinderHook:createHookHandle");
        return new ISubBinderHookHandle(mHostContext);
    }
}
