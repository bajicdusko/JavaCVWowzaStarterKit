package com.bajicdusko.javacvwowzastarterkit;

import android.hardware.Camera;
import android.view.SurfaceHolder;

import com.bajicdusko.javacvwowzastarterkit.exceptions.NoCameraDeviceFoundException;

import java.io.IOException;

/**
 * Created by Bajic Dusko (www.bajicdusko.com) on 30-Oct-16.
 */

public class CameraManager {

    public static final int NO_ACTIVE_CAMERA = -1;

    private int activeCameraId = NO_ACTIVE_CAMERA;
    private Camera camera;
    private boolean isInPreview;

    public Camera getBackCamera() throws NoCameraDeviceFoundException {
        return getCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
    }

    public Camera getFrontCamera() throws NoCameraDeviceFoundException {
        return getCamera(Camera.CameraInfo.CAMERA_FACING_FRONT);
    }

    private Camera getCamera(int cameraId) throws NoCameraDeviceFoundException {
        if (Camera.getNumberOfCameras() == 0) {
            throw new NoCameraDeviceFoundException();
        }

        Camera camera = Camera.open(cameraId);
        if (camera == null) {
            throw new NoCameraDeviceFoundException();
        }

        return camera;
    }

    public Camera getCamera() {
        return camera;
    }

    public void initCamera() throws NoCameraDeviceFoundException {
        if (camera == null) {
            try {
                camera = getBackCamera();
            } catch (NoCameraDeviceFoundException noCameraDeviceFound) {
                noCameraDeviceFound.printStackTrace();
            }
        }

        if (camera == null) {
            try {
                camera = getFrontCamera();
            } catch (NoCameraDeviceFoundException e) {

            }
        }

        if (camera == null) {
            throw new NoCameraDeviceFoundException();
        }
    }

    public void releaseCamera() {
        if (camera != null) {
            camera.stopPreview();
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
        }
    }

    public void initPreview(SurfaceHolder holder, Camera.PreviewCallback previewCallback) throws IOException {
        camera.setPreviewDisplay(holder);
        camera.setPreviewCallback(previewCallback);
    }

    public void startPreview() {
        if (!isInPreview) {
            camera.startPreview();
        }
    }

    public void stopPreview() {
        if (isInPreview) {
            camera.stopPreview();
        }
    }
}
