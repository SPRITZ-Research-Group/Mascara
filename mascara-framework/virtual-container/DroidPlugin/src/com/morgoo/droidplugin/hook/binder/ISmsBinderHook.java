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
import com.morgoo.droidplugin.hook.handle.ISmsHookHandle;
import com.morgoo.helper.compat.ISmsCompat;

/**
 * Created by Andy Zhang(zhangyong232@gmail.com) on 2016/5/9.
 */
public class ISmsBinderHook extends BinderHook {

    private static final String SERVICE_NAME = "isms";

    public ISmsBinderHook(Context hostContext) {
        super(hostContext);
        System.out.println("ISmsBinderHook:ISmsBinderHook");
    }

    @Override
    Object getOldObj() throws Exception {
        System.out.println("ISmsBinderHook:getOldObj");
        IBinder iBinder = MyServiceManager.getOriginService(SERVICE_NAME);
        return ISmsCompat.asInterface(iBinder);
    }

    @Override
    public String getServiceName() {
        System.out.println("ISmsBinderHook:getServiceName");
        return SERVICE_NAME;
    }

    @Override
    protected BaseHookHandle createHookHandle() {
        System.out.println("ISmsBinderHook:createHookHandle");
        return new ISmsHookHandle(mHostContext);
    }
}
