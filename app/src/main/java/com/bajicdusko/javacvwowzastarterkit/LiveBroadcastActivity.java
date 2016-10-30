package com.bajicdusko.javacvwowzastarterkit;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnNeverAskAgain;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.OnShowRationale;
import permissions.dispatcher.PermissionRequest;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions
public class LiveBroadcastActivity extends AppCompatActivity implements Camera.PreviewCallback, SurfaceHolder.Callback {

    private final static String CLASS_LABEL = "LiveBroadcastActivity";
    private final static String LOG_TAG = CLASS_LABEL;
    private final String EMPTY_STRING = "";

    private final int NO_ACTIVE_CAMERA = -1;
    private final int BACK_CAMERA_ID = Camera.CameraInfo.CAMERA_FACING_BACK;
    private final int FRONT_CAMERA_ID = Camera.CameraInfo.CAMERA_FACING_FRONT;
    private final String PROTOCOL = "rtmp://";

    @BindView(R.id.activity_live_broadcast_sv_video)
    SurfaceView svVideo;
    @BindView(R.id.activity_live_broadcast_bt_toggle)
    ImageView btToggle;
    @BindView(R.id.activity_live_broadcast_bt_broadcast)
    ImageView btBroadcast;
    @BindView(R.id.activity_live_broadcast_bt_flash)
    ImageView btFlash;

    //broadcast credentials (if stream source requires authentication)
    private String wowzaUsername;
    private String wowzaPassword;
    private String wowzaIp = "xxx.xxx.xxx.xxx";
    private int wowzaLivePort = 1935; //by default Wowza settings
    private String wowzaApplicationName = "live";
    private String wowzaStreamName = "android";

    private int sampleAudioRateInHz = 44100;
    private int imageWidth;
    private int imageHeight;
    private int frameRate = 30;
    private int VIDEO_BITRATE = 700 * 1000;
    private int AUDIO_BITRATE = 8 * 1000;

    /* audio data getting thread */
    private AudioRecord audioRecord;
    private AudioRecordRunnable audioRecordRunnable;
    private Thread audioThread;
    volatile boolean runAudioThread = true;

    /* video data getting thread */
    private Camera cameraDevice;
    private FFmpegFrameRecorder recorder;

    private Frame yuvImage = null;

    long startTime = 0;
    boolean recording = false;
    private boolean isPreviewOn = false;
    private int screenHeight;
    private int screenWidth;
    private final int videoWidth = 854;
    private final int videoHeight = 480;

    private boolean isFlashOn = false;
    private int activeCameraId = NO_ACTIVE_CAMERA;
    private boolean cameraToggleEnabled = true;
    private boolean continueRecordingOnSwitch = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        setContentView(R.layout.activity_live_broadcast);
        ButterKnife.bind(this);

        screenWidth = getResources().getDisplayMetrics().widthPixels;
        screenHeight = getResources().getDisplayMetrics().heightPixels;
        setSurfaceSize(videoWidth, videoHeight);

        LiveBroadcastActivityPermissionsDispatcher.initCameraWithCheck(this);
    }

    private void setSurfaceSize(int videoWidth, int videoHeight) {
        svVideo.getLayoutParams().width = (int) (screenWidth * ((float) videoWidth / (float) videoHeight));
    }

    @NeedsPermission({Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO})
    public void initCamera() {
        try {

            if (cameraDevice != null) {
                cameraDevice.setPreviewCallback(null);
                cameraDevice.release();
            }

            if (Camera.getNumberOfCameras() == 0) {
                Toast.makeText(this, "Device does not have camera.", Toast.LENGTH_SHORT).show();
            } else {
                if (Camera.getNumberOfCameras() == 1) {
                    cameraToggleEnabled = false;
                    btToggle.setEnabled(false);
                }

                if (activeCameraId == NO_ACTIVE_CAMERA) {
                    Camera backCamera = Camera.open(BACK_CAMERA_ID);
                    if (backCamera != null) {
                        activeCameraId = BACK_CAMERA_ID;
                        cameraDevice = backCamera;
                    } else {
                        Camera frontCamera = Camera.open(FRONT_CAMERA_ID);
                        if (frontCamera != null) {
                            activeCameraId = FRONT_CAMERA_ID;
                            cameraDevice = frontCamera;
                        } else {
                            Toast.makeText(this, "Front camera could not be initiated", Toast.LENGTH_SHORT).show();
                        }
                    }

                    if (cameraDevice != null) {
                        SurfaceHolder mHolder = svVideo.getHolder();
                        mHolder.addCallback(this);
                        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
                    }
                } else {
                    //used for switching purposes
                    cameraDevice = Camera.open(activeCameraId);
                    cameraDevice.setPreviewDisplay(svVideo.getHolder());
                    cameraDevice.setPreviewCallback(this);
                    cameraDevice.startPreview();
                    if (continueRecordingOnSwitch) {
                        startRecording();
                    }
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error occured while accessing camera.", Toast.LENGTH_SHORT).show();
        }
    }

    @OnClick(R.id.activity_live_broadcast_sv_video)
    public void onSurfaceClick() {
        if (cameraDevice != null) {
            cameraDevice.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean b, Camera camera) {

                }
            });
        }
    }

    @OnClick(R.id.activity_live_broadcast_bt_broadcast)
    public void onRecordClick() {
        if (!recording) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    @OnClick(R.id.activity_live_broadcast_bt_flash)
    public void onFlashClick() {
        if (cameraDevice != null) {
            if (getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
                Camera.Parameters params = cameraDevice.getParameters();
                params.setFlashMode(isFlashOn == false ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
                isFlashOn = !isFlashOn;
                btFlash.setImageResource(isFlashOn ? R.drawable.ic_flash_off : R.drawable.ic_flash_on);
            } else {
                Toast.makeText(this, "Camera does not have flashlight", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @OnClick(R.id.activity_live_broadcast_bt_toggle)
    public void onToggleCameraClick() {
        if (cameraToggleEnabled && activeCameraId != NO_ACTIVE_CAMERA) {
            continueRecordingOnSwitch = true;
            stopRecording();
            stopPreview();
            activeCameraId = activeCameraId == BACK_CAMERA_ID ? FRONT_CAMERA_ID : BACK_CAMERA_ID;
            LiveBroadcastActivityPermissionsDispatcher.initCameraWithCheck(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        recording = false;

        try {
            stopPreview();
        } catch (Exception ex) {

        }

        if (cameraDevice != null) {
            cameraDevice.stopPreview();
            cameraDevice.release();
            cameraDevice = null;
        }
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        if (audioRecord == null || audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            startTime = System.currentTimeMillis();
            return;
        }

            /* get video data */
        if (yuvImage != null && recording) {
            ((ByteBuffer) yuvImage.image[0].position(0)).put(bytes);

            try {
                Log.v(LOG_TAG, "Writing Frame");
                long t = 1000 * (System.currentTimeMillis() - startTime);
                if (t > recorder.getTimestamp()) {
                    recorder.setTimestamp(t);
                }
                recorder.record(yuvImage);
            } catch (FFmpegFrameRecorder.Exception e) {
                Log.v(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            stopPreview();
            cameraDevice.setPreviewDisplay(holder);
        } catch (IOException exception) {
            cameraDevice.release();
            cameraDevice = null;
        }
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        stopPreview();

        Camera.Parameters camParams = cameraDevice.getParameters();
        List<Camera.Size> sizes = camParams.getSupportedPreviewSizes();
        Collections.sort(sizes, new Comparator<Camera.Size>() {

            public int compare(final Camera.Size a, final Camera.Size b) {
                return a.width * a.height - b.width * b.height;
            }
        });

        // Pick the first preview size that is equal or bigger, or pick the last (biggest) option if we cannot
        // reach the initial settings of imageWidth/imageHeight.
        for (int i = 0; i < sizes.size(); i++) {
            if ((sizes.get(i).width >= videoWidth && sizes.get(i).height >= videoHeight) || i == sizes.size() - 1) {
                imageWidth = sizes.get(i).width;
                imageHeight = sizes.get(i).height;
                Log.v(LOG_TAG, "Changed to supported resolution: " + imageWidth + "x" + imageHeight);
                break;
            }
        }
        camParams.setPreviewSize(imageWidth, imageHeight);
        setSurfaceSize(imageWidth, imageHeight);

        Log.v(LOG_TAG, "Setting imageWidth: " + imageWidth + " imageHeight: " + imageHeight + " frameRate: " + frameRate);

        camParams.setPreviewFrameRate(frameRate);
        Log.v(LOG_TAG, "Preview Framerate: " + camParams.getPreviewFrameRate());

        cameraDevice.setParameters(camParams);

        try {
            cameraDevice.setPreviewDisplay(holder);
            cameraDevice.setPreviewCallback(LiveBroadcastActivity.this);
            startPreview();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Could not set preview display in surfaceChanged");
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        try {
            holder.addCallback(null);
            cameraDevice.setPreviewCallback(null);
        } catch (RuntimeException e) {
        }
    }

    private void startPreview() {
        if (!isPreviewOn && cameraDevice != null) {
            isPreviewOn = true;
            cameraDevice.startPreview();
        }
    }

    public void stopPreview() {
        if (isPreviewOn && cameraDevice != null) {
            isPreviewOn = false;
            cameraDevice.stopPreview();
        }
    }

    private void initRecorder() {

        Log.w(LOG_TAG, "init recorder");
        if (yuvImage == null) {
            yuvImage = new Frame(imageWidth, imageHeight, Frame.DEPTH_UBYTE, 2);
            Log.i(LOG_TAG, "create yuvImage");
        }

        String streamUrl = buildWowzaStreamEndpoint();
        if (!TextUtils.isEmpty(streamUrl)) {
            Log.i(LOG_TAG, "Stream url: " + streamUrl);
            recorder = new FFmpegFrameRecorder(streamUrl, imageWidth, imageHeight, 1);
            recorder.setFormat("flv");
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setVideoBitrate(VIDEO_BITRATE);
            recorder.setAudioBitrate(AUDIO_BITRATE);
            recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
            recorder.setSampleRate(sampleAudioRateInHz);
            recorder.setFrameRate(frameRate);

            Log.i(LOG_TAG, "recorder initialize success");

            audioRecordRunnable = new AudioRecordRunnable();
            audioThread = new Thread(audioRecordRunnable);
            runAudioThread = true;
        }
    }

    private String buildWowzaStreamEndpoint() {

        StringBuilder builder = new StringBuilder();
        builder.append(PROTOCOL);
        if (!TextUtils.isEmpty(wowzaUsername) && !TextUtils.isEmpty(wowzaPassword)) {
            builder.append(wowzaUsername).append(":").append(wowzaPassword).append("@");
        }

        if (wowzaIp.contains("x")) {
            Toast.makeText(this, "Wowza IP address not set. Check \"wowzaIp\" property.", Toast.LENGTH_SHORT).show();
        } else if (TextUtils.isEmpty(wowzaApplicationName)) {
            Toast.makeText(this, "Wowza application name not set. Check \"wowzaApplicationName\" property.", Toast.LENGTH_SHORT).show();
        } else if (TextUtils.isEmpty(wowzaStreamName)) {
            Toast.makeText(this, "Wowza stream name not set. Check \"wowzaStreamName\" property.", Toast.LENGTH_SHORT).show();
        } else {
            builder.append(wowzaIp).append(":").append(wowzaLivePort).append("/");
            builder.append(wowzaApplicationName).append("/");
            builder.append(wowzaStreamName);
            return builder.toString();
        }

        return EMPTY_STRING;
    }

    public void startRecording() {
        btBroadcast.setImageResource(R.drawable.ic_broadcast_stop);
        initRecorder();

        try {
            recorder.start();
            startTime = System.currentTimeMillis();
            recording = true;
            audioThread.start();

        } catch (FFmpegFrameRecorder.Exception e) {
            e.printStackTrace();
            btBroadcast.setImageResource(R.drawable.ic_broadcast);
        }
    }

    public void stopRecording() {

        if (!continueRecordingOnSwitch) {
            btBroadcast.setImageResource(R.drawable.ic_broadcast);
        }
        runAudioThread = false;
        try {
            if (audioThread != null) {
                audioThread.join();
            }
        } catch (InterruptedException e) {
            // reset interrupt to be nice
            Thread.currentThread().interrupt();
            return;
        }
        audioRecordRunnable = null;
        audioThread = null;

        if (recorder != null && recording) {

            recording = false;
            Log.v(LOG_TAG, "Finishing recording, calling stop and release on recorder");
            try {
                recorder.stop();
                recorder.release();
            } catch (FFmpegFrameRecorder.Exception e) {
                e.printStackTrace();
            }
            recorder = null;

        }
    }

    @Override
    public void onBackPressed() {
        if (recording) {
            stopRecording();
        }
        super.onBackPressed();
    }

    class AudioRecordRunnable implements Runnable {

        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

            // Audio
            int bufferSize;
            ShortBuffer audioData;
            int bufferReadResult;

            bufferSize = AudioRecord.getMinBufferSize(sampleAudioRateInHz,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleAudioRateInHz,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

            audioData = ShortBuffer.allocate(bufferSize);

            Log.d(LOG_TAG, "audioRecord.startRecording()");
            audioRecord.startRecording();

            /* ffmpeg_audio encoding loop */
            while (runAudioThread) {

                bufferReadResult = audioRecord.read(audioData.array(), 0, audioData.capacity());
                audioData.limit(bufferReadResult);
                if (bufferReadResult > 0) {
                    Log.v(LOG_TAG, "bufferReadResult: " + bufferReadResult);
                    if (recording) {
                        try {
                            recorder.recordSamples(audioData);
                        } catch (FFmpegFrameRecorder.Exception e) {
                            Log.v(LOG_TAG, e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            }
            Log.v(LOG_TAG, "AudioThread Finished, release audioRecord");

            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
                Log.v(LOG_TAG, "audioRecord released");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        LiveBroadcastActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    @OnShowRationale({Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO})
    public void showRationaleDialog(final PermissionRequest permissionRequest) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Why do we need these permissions?");
        builder.setMessage("In order to stream video and audio from this device, we need to be able to access Camera and Microphone.");
        builder.setPositiveButton("Ok, continue", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                permissionRequest.proceed();
            }
        });
        builder.setNegativeButton("Deny for now", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                permissionRequest.cancel();
            }
        });
        builder.create().show();
    }

    @OnPermissionDenied({Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO})
    public void onPermissionDenied() {
        Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
    }

    @OnNeverAskAgain({Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO})
    public void onNeverAskAgain() {

    }
}