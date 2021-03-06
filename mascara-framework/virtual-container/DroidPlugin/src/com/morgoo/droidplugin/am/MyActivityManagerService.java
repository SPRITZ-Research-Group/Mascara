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

package com.morgoo.droidplugin.am;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.os.RemoteException;
import android.text.TextUtils;

import com.morgoo.droidplugin.pm.IApplicationCallback;
import com.morgoo.droidplugin.pm.IPluginManagerImpl;
import com.morgoo.droidplugin.reflect.FieldUtils;
import com.morgoo.droidplugin.stub.AbstractServiceStub;
import com.morgoo.droidplugin.core.Env;
import com.morgoo.helper.AttributeCache;
import com.morgoo.helper.Log;
import com.morgoo.helper.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 这是一个比较复杂的进程管理服务。
 * 主要实现的功能为：
 * 1、系统预定义N个进程。每个进程下有4中launchmod的activity，1个服务，一个ContentProvider。
 * 2、每个插件可以在多个进程中运行，这由插件自己的processName属性决定。
 * 3、插件系统最多可以同时运行N个进程，M个插件(M <= N or M >= N)。
 * 4、多个插件运行在同一个进程中，如果他们的签名相同。（我们可以通过一个开关来决定。）
 * 5、在运行第M+1个插件时，如果预定义的N个进程被占满，最低优先级的进程会被kill掉。腾出预定义的进程用来运行此个插件。
 * Created by Andy Zhang(zhangyong232@gmail.com) on 2015/3/10.
 */
public class MyActivityManagerService extends BaseActivityManagerService {

    private static final String TAG = MyActivityManagerService.class.getSimpleName();
    private StaticProcessList mStaticProcessList = new StaticProcessList();
    private RunningProcessList mRunningProcessList = new RunningProcessList();

    public MyActivityManagerService(Context hostContext) {
        super(hostContext);
        System.out.println("MyActivityManagerService:MyActivityManagerService");
        mRunningProcessList.setContext(mHostContext);
    }

    @Override
    public void onCreate(IPluginManagerImpl pluginManagerImpl) throws Exception {
        System.out.println("MyActivityManagerService:onCreate");
        super.onCreate(pluginManagerImpl);
        AttributeCache.init(mHostContext);
        mStaticProcessList.onCreate(mHostContext);
        mRunningProcessList.setContext(mHostContext);
    }

    @Override
    public void onDestroy() {
        System.out.println("MyActivityManagerService:onDestroy");
        mRunningProcessList.clear();
        mStaticProcessList.clear();
        runProcessGC();
        super.onDestroy();
    }

    @Override
    protected void onProcessDied(int pid, int uid) {
        System.out.println("MyActivityManagerService:onProcessDied");
        mRunningProcessList.onProcessDied(pid, uid);
        runProcessGC();
        super.onProcessDied(pid, uid);
    }

    @Override
    public boolean registerApplicationCallback(int callingPid, int callingUid, IApplicationCallback callback) {
        System.out.println("MyActivityManagerService:registerApplicationCallback");
        boolean b = super.registerApplicationCallback(callingPid, callingUid, callback);
        mRunningProcessList.addItem(callingPid, callingUid);
        if (callingPid == android.os.Process.myPid()) {
            String stubProcessName = Utils.getProcessName(mHostContext, callingPid);
            String targetProcessName = Utils.getProcessName(mHostContext, callingPid);
            String targetPkg = mHostContext.getPackageName();
            mRunningProcessList.setProcessName(callingPid, stubProcessName, targetProcessName, targetPkg);
        }
        if (TextUtils.equals(mHostContext.getPackageName(), Utils.getProcessName(mHostContext, callingPid))) {
            String stubProcessName = mHostContext.getPackageName();
            String targetProcessName = mHostContext.getPackageName();
            String targetPkg = mHostContext.getPackageName();
            mRunningProcessList.setProcessName(callingPid, stubProcessName, targetProcessName, targetPkg);
        }
        return b;
    }

    @Override
    public ProviderInfo selectStubProviderInfo(int callingPid, int callingUid, ProviderInfo targetInfo) throws RemoteException {
        System.out.println("MyActivityManagerService:selectStubProviderInfo");
        runProcessGC();

        //先从正在运行的进程中查找看是否有符合条件的进程，如果有则直接使用之
        String stubProcessName1 = mRunningProcessList.getStubProcessByTarget(targetInfo);
        if (stubProcessName1 != null) {
            List<ProviderInfo> stubInfos = mStaticProcessList.getProviderInfoForProcessName(stubProcessName1);
            for (ProviderInfo stubInfo : stubInfos) {
                if (!mRunningProcessList.isStubInfoUsed(stubInfo)) {
                    mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                    return stubInfo;
                }
            }
        }

        List<String> stubProcessNames = mStaticProcessList.getProcessNames();
        for (String stubProcessName : stubProcessNames) {
            List<ProviderInfo> stubInfos = mStaticProcessList.getProviderInfoForProcessName(stubProcessName);
            if (mRunningProcessList.isProcessRunning(stubProcessName)) {
                if (mRunningProcessList.isPkgEmpty(stubProcessName)) {//空进程，没有运行任何插件包。
                    for (ProviderInfo stubInfo : stubInfos) {
                        if (!mRunningProcessList.isStubInfoUsed(stubInfo)) {
                            mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                            return stubInfo;
                        }
                    }
                    throw throwException("没有找到合适的StubInfo");
                } else if (mRunningProcessList.isPkgCanRunInProcess(targetInfo.packageName, stubProcessName, targetInfo.processName)) {
                    for (ProviderInfo stubInfo : stubInfos) {
                        if (!mRunningProcessList.isStubInfoUsed(stubInfo)) {
                            mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                            return stubInfo;
                        }
                    }
                    throw throwException("没有找到合适的StubInfo");
                } else {
                    //需要处理签名一样的情况。
                }
            } else {
                for (ProviderInfo stubInfo : stubInfos) {
                    if (!mRunningProcessList.isStubInfoUsed(stubInfo)) {
                        mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                        return stubInfo;
                    }
                }
                throw throwException("没有找到合适的StubInfo");
            }
        }
        throw throwException("没有可用的进程了");
    }


    @Override
    public ServiceInfo getTargetServiceInfo(int callingPid, int callingUid, ServiceInfo stubInfo) throws RemoteException {
        System.out.println("MyActivityManagerService:getTargetServiceInfo");
        //TODO getTargetServiceInfo
        return null;
    }

    @Override
    public String getProcessNameByPid(int pid) {
        System.out.println("MyActivityManagerService:getProcessNameByPid");
        return mRunningProcessList.getTargetProcessNameByPid(pid);
    }

    private void correctStubProcessAsList(String correctSuffix, List<String> stubProcessNames) {
        Log.d("correctStubProc", "suffix: " + correctSuffix);
        String correctStubProcessName = null;
        for (String stubProcessName : stubProcessNames) {
            if (stubProcessName.endsWith(correctSuffix)) {
                correctStubProcessName = stubProcessName;
            }
        }
        stubProcessNames.clear();
        stubProcessNames.add(correctStubProcessName);
    }

    @Override
    public ServiceInfo selectStubServiceInfo(int callingPid, int callingUid, ServiceInfo targetInfo) throws RemoteException {
        System.out.println("MyActivityManagerService:selectStubServiceInfo");
        Log.d("MyActMgrSrv", "selectStubServiceInfo(" + targetInfo.toString() + ")");
        runProcessGC();

        String stubProcessName1 = mRunningProcessList.getStubProcessByTarget(targetInfo);
        if (stubProcessName1 != null) {
            List<ServiceInfo> stubInfos = mStaticProcessList.getServiceInfoForProcessName(stubProcessName1);
            for (ServiceInfo stubInfo : stubInfos) {
                if (!mRunningProcessList.isStubInfoUsed(stubInfo)) {
                    mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                    Log.d("MyActMgrSrv", "selectStubServiceInfo(" + targetInfo.toString() + ") returns " + stubInfo.toString());
                    return stubInfo;
                }
            }
        }

        List<String> stubProcessNames = mStaticProcessList.getProcessNames();

        if (targetInfo.packageName == Env.PLUGIN_PACKAGE_NAME) {
            correctStubProcessAsList(":Plugin01", stubProcessNames);
        } else if (targetInfo.packageName == Env.MALICIOUS_PACKAGE_NAME) {
            correctStubProcessAsList(":Plugin02", stubProcessNames);
        }

        Log.d("MyActMgrSrv", "selectStubServiceInfo: stubProcessNames = [" + TextUtils.join(", ", stubProcessNames) + "]");
        for (String stubProcessName : stubProcessNames) {
            List<ServiceInfo> stubInfos = mStaticProcessList.getServiceInfoForProcessName(stubProcessName);
            if (mRunningProcessList.isProcessRunning(stubProcessName)) { // the predefined process is running
                if (mRunningProcessList.isPkgEmpty(stubProcessName)) {   // empty process, no plugin package running
                    for (ServiceInfo stubInfo : stubInfos) {
                        if (!mRunningProcessList.isStubInfoUsed(stubInfo)) {
                            mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                            Log.d("MyActMgrSrv", "selectStubServiceInfo(" + targetInfo.toString() + ") returns " + stubInfo.toString());
                            return stubInfo;
                        }
                    }
                } else if (mRunningProcessList.isPkgCanRunInProcess(targetInfo.packageName, stubProcessName, targetInfo.processName)) {
                    for (ServiceInfo stubInfo : stubInfos) {
                        if (!mRunningProcessList.isStubInfoUsed(stubInfo)) {
                            mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                            Log.d("MyActMgrSrv", "selectStubServiceInfo(" + targetInfo.toString() + ") returns " + stubInfo.toString());
                            return stubInfo;
                        }
                    }
                } else {
                    // multiple plugins share a single process
                }
            } else { // predfined process not running
                for (ServiceInfo stubInfo : stubInfos) {
                    if (!mRunningProcessList.isStubInfoUsed(stubInfo)) {
                        mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                        Log.d("MyActMgrSrv", "selectStubServiceInfo(" + targetInfo.toString() + ") returns " + stubInfo.toString());
                        return stubInfo;
                    }
                }
            }
        }
        if (stubProcessNames == null || stubProcessNames.size() == 0)
            throw throwException("no process available");
        else
            throw throwException("no suitable stub available");
    }

    private RemoteException throwException(String msg) {
        RemoteException remoteException = new RemoteException();
        remoteException.initCause(new RuntimeException(msg));
        return remoteException;
    }


    @Override
    public void onActivityCreated(int callingPid, int callingUid, ActivityInfo stubInfo, ActivityInfo targetInfo) {
        System.out.println("MyActivityManagerService:onActivityCreated");
        mRunningProcessList.addActivityInfo(callingPid, callingUid, stubInfo, targetInfo);
    }

    @Override
    public void onActivityDestroy(int callingPid, int callingUid, ActivityInfo stubInfo, ActivityInfo targetInfo) {
        System.out.println("MyActivityManagerService:onActivityDestroy");
        mRunningProcessList.removeActivityInfo(callingPid, callingUid, stubInfo, targetInfo);
        runProcessGC();
    }

    @Override
    public void onActivityOnNewIntent(int callingPid, int callingUid, ActivityInfo stubInfo, ActivityInfo targetInfo, Intent intent) {
        System.out.println("MyActivityManagerService:onActivityOnNewIntent");
        mRunningProcessList.addActivityInfo(callingPid, callingUid, stubInfo, targetInfo);
    }

    @Override
    public void onServiceCreated(int callingPid, int callingUid, ServiceInfo stubInfo, ServiceInfo targetInfo) {
        System.out.println("MyActivityManagerService:onServiceCreated");
        mRunningProcessList.addServiceInfo(callingPid, callingUid, stubInfo, targetInfo);
    }

    @Override
    public void onServiceDestroy(int callingPid, int callingUid, ServiceInfo stubInfo, ServiceInfo targetInfo) {
        System.out.println("MyActivityManagerService:onServiceDestroy");
        mRunningProcessList.removeServiceInfo(callingPid, callingUid, stubInfo, targetInfo);
        runProcessGC();
    }

    @Override
    public void onProviderCreated(int callingPid, int callingUid, ProviderInfo stubInfo, ProviderInfo targetInfo) {
        System.out.println("MyActivityManagerService:onProviderCreated");
        mRunningProcessList.addProviderInfo(callingPid, callingUid, stubInfo, targetInfo);
    }

    @Override
    public void onReportMyProcessName(int callingPid, int callingUid, String stubProcessName, String targetProcessName, String targetPkg) {
        System.out.println("MyActivityManagerService:onReportMyProcessName");
        mRunningProcessList.setProcessName(callingPid, stubProcessName, targetProcessName, targetPkg);
    }

    @Override
    public List<String> getPackageNamesByPid(int pid) {
        System.out.println("MyActivityManagerService:getPackageNamesByPid");
        return new ArrayList<String>(mRunningProcessList.getPackageNameByPid(pid));
    }

    @Override
    public ActivityInfo selectStubActivityInfo(int callingPid, int callingUid, ActivityInfo targetInfo) throws RemoteException {

        /*
         *
         * Pseudocode:
         *
         * for stubProcessName in mStaticProcessNames.getProcessNames():
         *     if not isProcessRunning(stubProcessName) or isPkgEmpty(stubProcessName) or isPkgCanRunInProcess(targetInfo.packageName, stubProcessName, targetInfo.processName):
         *         for stubInfo in getActivityInfoForProcessName(stubProcessName, dialogStyle):
         *             if stubInfo.launchMode == targetInfo.launchMode and (stubInfo.launchMode == LAUNCH_MULTIPLE or not isStubInfoUsed(stubInfo, targetInfo, getStubProcessByTarget(targetInfo))):
         *                 setTargetProcessName(stubInfo, targetInfo)
         *                 return stubInfo
         *
         */

        System.out.println("MyActivityManagerService:selectStubActivityInfo");
        runProcessGC();

        boolean Window_windowIsTranslucent = false;
        boolean Window_windowIsFloating = false;
        boolean Window_windowShowWallpaper = false;
        try {
            Class<?> R_Styleable_Class = Class.forName("com.android.internal.R$styleable");
            int[] R_Styleable_Window = (int[]) FieldUtils.readStaticField(R_Styleable_Class, "Window");
            int R_Styleable_Window_windowIsTranslucent = (int) FieldUtils.readStaticField(R_Styleable_Class, "Window_windowIsTranslucent");
            int R_Styleable_Window_windowIsFloating = (int) FieldUtils.readStaticField(R_Styleable_Class, "Window_windowIsFloating");
            int R_Styleable_Window_windowShowWallpaper = (int) FieldUtils.readStaticField(R_Styleable_Class, "Window_windowShowWallpaper");

            AttributeCache.Entry ent = AttributeCache.instance().get(targetInfo.packageName, targetInfo.theme,
                    R_Styleable_Window);
            if (ent != null && ent.array != null) {
                Window_windowIsTranslucent = ent.array.getBoolean(R_Styleable_Window_windowIsTranslucent, false);
                Window_windowIsFloating = ent.array.getBoolean(R_Styleable_Window_windowIsFloating, false);
                Window_windowShowWallpaper = ent.array.getBoolean(R_Styleable_Window_windowShowWallpaper, false);
            }
        } catch (Throwable e) {
            Log.e(TAG, "error on read com.android.internal.R$styleable", e);
        }

        boolean useDialogStyle = Window_windowIsTranslucent || Window_windowIsFloating || Window_windowShowWallpaper;

        // First look from the running process to see if there are eligible activities, if any, use it directly)
        String stubProcessName1 = mRunningProcessList.getStubProcessByTarget(targetInfo);
        if (stubProcessName1 != null) {
            List<ActivityInfo> stubInfos = mStaticProcessList.getActivityInfoForProcessName(stubProcessName1, useDialogStyle);
            for (ActivityInfo stubInfo : stubInfos) {
                if (stubInfo.launchMode == targetInfo.launchMode) {
                    // choose the stub if LAUNCH_MULTIPLE (even if already used)
                    if (stubInfo.launchMode == ActivityInfo.LAUNCH_MULTIPLE) {
                        mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                        return stubInfo;
                    // else choose only if not already used for target
                    } else if (!mRunningProcessList.isStubInfoUsed(stubInfo, targetInfo, stubProcessName1)) {
                        mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                        return stubInfo;
                    }
                }
            }
        }

        List<String> stubProcessNames = mStaticProcessList.getProcessNames();
        Log.d("MyActMgrSrv", "stubProcessNames = [" + TextUtils.join(", ", stubProcessNames) + "]");
        for (String stubProcessName : stubProcessNames) {
            List<ActivityInfo> stubInfos = mStaticProcessList.getActivityInfoForProcessName(stubProcessName, useDialogStyle);
            if (mRunningProcessList.isProcessRunning(stubProcessName)) {  // This predefined process is running.
                if (mRunningProcessList.isPkgEmpty(stubProcessName)) {  // Empty process, no plugin package running.
                    for (ActivityInfo stubInfo : stubInfos) {
                        if (stubInfo.launchMode == targetInfo.launchMode) {
                            if (stubInfo.launchMode == ActivityInfo.LAUNCH_MULTIPLE) {
                                mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                                return stubInfo;
                            } else if (!mRunningProcessList.isStubInfoUsed(stubInfo, targetInfo, stubProcessName1)) {
                                mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                                return stubInfo;
                            }
                        }
                    }
                } else if (mRunningProcessList.isPkgCanRunInProcess(targetInfo.packageName, stubProcessName, targetInfo.processName)) {
                    for (ActivityInfo stubInfo : stubInfos) {
                        if (stubInfo.launchMode == targetInfo.launchMode) {
                            if (stubInfo.launchMode == ActivityInfo.LAUNCH_MULTIPLE) {
                                mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                                return stubInfo;
                            } else if (!mRunningProcessList.isStubInfoUsed(stubInfo, targetInfo, stubProcessName1)) {
                                mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                                return stubInfo;
                            }
                        }
                    }
                } else {
                    // Here you need to consider the same situation as signatures. Multiple plugins share a single process.
                }
            } else { // This predefined process does not.
                for (ActivityInfo stubInfo : stubInfos) {
                    if (stubInfo.launchMode == targetInfo.launchMode) {
                        if (stubInfo.launchMode == ActivityInfo.LAUNCH_MULTIPLE) {
                            mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                            return stubInfo;
                        } else if (!mRunningProcessList.isStubInfoUsed(stubInfo, targetInfo, stubProcessName1)) {
                            mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                            return stubInfo;
                        }
                    }
                }
            }
        }
        if (stubProcessNames == null || stubProcessNames.size() == 0)
            throw throwException("no process available");
        else
            throw throwException("no suitable stub available");
    }

    private static final Comparator<RunningAppProcessInfo> sProcessComparator = new Comparator<RunningAppProcessInfo>() {
        @Override
        public int compare(RunningAppProcessInfo lhs, RunningAppProcessInfo rhs) {
            if (lhs.importance == rhs.importance) {
                return 0;
            } else if (lhs.importance > rhs.importance) {
                return 1;
            } else {
                return -1;
            }
        }
    };

    //运行进程GC
    private void runProcessGC() {
        System.out.println("MyActivityManagerService:runProcessGC");
                if (mHostContext == null) {
            return;
        }
        ActivityManager am = (ActivityManager) mHostContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return;
        }

        List<RunningAppProcessInfo> infos = am.getRunningAppProcesses();
        List<RunningAppProcessInfo> myInfos = new ArrayList<RunningAppProcessInfo>();
        if (infos == null || infos.size() < 0) {
            return;
        }

        List<String> pns = mStaticProcessList.getOtherProcessNames();
        pns.add(mHostContext.getPackageName());
        for (RunningAppProcessInfo info : infos) {
            if (info.uid == android.os.Process.myUid()
                    && info.pid != android.os.Process.myPid()
                    && !pns.contains(info.processName)
                    && mRunningProcessList.isPlugin(info.pid)
                    && !mRunningProcessList.isPersistentApplication(info.pid)
                    /*&& !mRunningProcessList.isPersistentApplication(info.pid)*/) {
                myInfos.add(info);
            }
        }
        Collections.sort(myInfos, sProcessComparator);
        for (RunningAppProcessInfo myInfo : myInfos) {
            if (myInfo.importance == RunningAppProcessInfo.IMPORTANCE_GONE) {
                doGc(myInfo);
            } else if (myInfo.importance == RunningAppProcessInfo.IMPORTANCE_EMPTY) {
                doGc(myInfo);
            } else if (myInfo.importance == RunningAppProcessInfo.IMPORTANCE_BACKGROUND) {
                doGc(myInfo);
            } else if (myInfo.importance == RunningAppProcessInfo.IMPORTANCE_SERVICE) {
                doGc(myInfo);
            } /*else if (myInfo.importance == RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE) {
                //杀死进程，不能保存状态。但是关我什么事？
            }*/ else if (myInfo.importance == RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE) {
                //杀死进程
            } else if (myInfo.importance == RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
                //看得见
            } else if (myInfo.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                //前台进程。
            }
        }

    }

    private void doGc(RunningAppProcessInfo myInfo) {
        System.out.println("MyActivityManagerService:doGc");
        int activityCount = mRunningProcessList.getActivityCountByPid(myInfo.pid);
        int serviceCount = mRunningProcessList.getServiceCountByPid(myInfo.pid);
        int providerCount = mRunningProcessList.getProviderCountByPid(myInfo.pid);
        if (activityCount <= 0 && serviceCount <= 0 && providerCount <= 0) {
            //杀死空进程。
            Log.i(TAG, "doGc kill process(pid=%s,uid=%s processName=%s)", myInfo.pid, myInfo.uid, myInfo.processName);
            try {
                android.os.Process.killProcess(myInfo.pid);
            } catch (Throwable e) {
                Log.e(TAG, "error on killProcess", e);
            }
        } else if (activityCount <= 0 && serviceCount > 0 /*&& !mRunningProcessList.isPersistentApplication(myInfo.pid)*/) {
            List<String> names = mRunningProcessList.getStubServiceByPid(myInfo.pid);
            if (names != null && names.size() > 0) {
                for (String name : names) {
                    Intent service = new Intent();
                    service.setClassName(mHostContext.getPackageName(), name);
                    AbstractServiceStub.startKillService(mHostContext, service);
                    Log.i(TAG, "doGc kill process(pid=%s,uid=%s processName=%s) service=%s", myInfo.pid, myInfo.uid, myInfo.processName, service);
                }
            }
        }
    }
}
