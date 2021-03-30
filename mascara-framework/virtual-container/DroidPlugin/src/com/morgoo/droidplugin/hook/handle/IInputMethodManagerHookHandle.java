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

package com.morgoo.droidplugin.hook.handle;

import android.content.Context;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import com.morgoo.droidplugin.hook.BaseHookHandle;
import com.morgoo.droidplugin.hook.HookedMethodHandler;

import java.lang.reflect.Method;

/**
 * Created by Andy Zhang(zhangyong232@gmail.com) on 2015/6/4.
 */
public class IInputMethodManagerHookHandle extends BaseHookHandle {

    public IInputMethodManagerHookHandle(Context hostContext) {
        super(hostContext);
        System.out.println("IInputMethodManagerHookHandle:IInputMethodManagerHookHandle");
    }

    @Override
    protected void init() {
        System.out.println("IInputMethodManagerHookHandle:init");
        sHookedMethodHandlers.put("startInput", new startInput(mHostContext));
        sHookedMethodHandlers.put("windowGainedFocus", new windowGainedFocus(mHostContext));
        sHookedMethodHandlers.put("startInputOrWindowGainedFocus", new startInputOrWindowGainedFocus(mHostContext));
    }

    private class IInputMethodManagerHookedMethodHandler extends HookedMethodHandler {
        public IInputMethodManagerHookedMethodHandler(Context hostContext) {
            super(hostContext);
            System.out.println("IInputMethodManagerHookedMethodHandler:IInputMethodManagerHookedMethodHandler");
        }

        @Override
        protected boolean beforeInvoke(Object receiver, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0) {
                for (Object arg : args) {
                    if (arg instanceof EditorInfo) {
                        EditorInfo info = ((EditorInfo) arg);
                        if (!TextUtils.equals(mHostContext.getPackageName(), info.packageName)) {
                            info.packageName = mHostContext.getPackageName();
                        }
                    }
                }
            }
            return super.beforeInvoke(receiver, method, args);
        }
    }

    private class startInput extends IInputMethodManagerHookedMethodHandler {
        public startInput(Context hostContext) {
            super(hostContext);
            System.out.println("startInput:startInput");
        }
    }

    private class windowGainedFocus extends IInputMethodManagerHookedMethodHandler {
        public windowGainedFocus(Context hostContext) {
            super(hostContext);
            System.out.println("windowGainedFocus:windowGainedFocus");
        }
    }

    private class startInputOrWindowGainedFocus extends IInputMethodManagerHookedMethodHandler {
        public startInputOrWindowGainedFocus(Context hostContext) {
            super(hostContext);
            System.out.println("startInputOrWindowGainedFocus:startInputOrWindowGainedFocus");
        }
    }

    private class finishInput extends IInputMethodManagerHookedMethodHandler {
        public finishInput(Context hostContext) {
            super(hostContext);
        }
    }

    private class addClient extends IInputMethodManagerHookedMethodHandler {
        public addClient(Context hostContext) {
            super(hostContext);
        }
    }


    private class removeClient extends IInputMethodManagerHookedMethodHandler {
        public removeClient(Context hostContext) {
            super(hostContext);
        }
    }
}
