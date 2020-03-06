package com.cannizarro.securitycamera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Layout;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;


public class CameraActivity extends AppCompatActivity {

    final String TAG = "CameraActivity";
    final int ALL_PERMISSIONS_CODE = 1;
    static final int MEDIA_TYPE_VIDEO = 1;
    private boolean isRecording = false, isScreenOn=true;

    private File file;
    private Camera camera;
    private MediaRecorder mediaRecorder;

    private View window;
    private Button captureButton, screenOff;
    private CameraPreview cameraPreview;

    FirebaseDatabase firebaseDatabase;
    DatabaseReference databaseReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        captureButton = findViewById(R.id.save);
        screenOff = findViewById(R.id.screenOff);
        window = findViewById(R.id.window);

        window.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!isScreenOn){
                    turnScreenOn();
                    isScreenOn = true;
                    screenOff.setText("Dim Screen");
                    captureButton.setEnabled(true);
                    screenOff.setEnabled(true);
                }
            }
        });

        screenOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                turnScreenOff();
                isScreenOn = false;
                captureButton.setEnabled(false);
                screenOff.setEnabled(false);
            }
        });

        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                controlRecording();
            }
        });



        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, ALL_PERMISSIONS_CODE);
        } else {
            // all permissions already granted
            start();
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == ALL_PERMISSIONS_CODE
                && grantResults.length == 4
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && grantResults[1] == PackageManager.PERMISSION_GRANTED
                && grantResults[2] == PackageManager.PERMISSION_GRANTED
                && grantResults[3] == PackageManager.PERMISSION_GRANTED) {
            // all permissions granted
            start();
        } else {
            finish();
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        releaseMediaRecorder();       // if you are using MediaRecorder, release it first
        releaseCamera();              // release the camera immediately on pause event
    }

    @Override
    protected void onResume() {
        super.onResume();
        start();
    }

    public void start(){

        camera = getCameraInstance();
        cameraPreview = new CameraPreview(CameraActivity.this, camera);
        FrameLayout preview = findViewById(R.id.camera_preview);
        preview.addView(cameraPreview);
        //prepareVideoRecorder();
    }

    private boolean prepareVideoRecorder(){

        mediaRecorder = new MediaRecorder();

        // Step 1: Unlock and set camera to MediaRecorder
        camera.unlock();
        mediaRecorder.setCamera(camera);

        // Step 2: Set sources
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
        mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_480P));

        // Step 4: Set output file
        file = getOutputMediaFile(MEDIA_TYPE_VIDEO);
        mediaRecorder.setOutputFile(file.toString());

        // Step 5: Set the preview output
        mediaRecorder.setPreviewDisplay(cameraPreview.getHolder().getSurface());

        // Step 6: Prepare configured MediaRecorder
        try {
            mediaRecorder.prepare();
        } catch (IllegalStateException e) {
            Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    public void controlRecording(){
        if(isRecording){
            // stop recording and release camera
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            mediaRecorder.stop();  // stop the recording
            releaseMediaRecorder(); // release the MediaRecorder object
            camera.lock();         // take camera access back from MediaRecorder

            // inform the user that recording has stopped
            setCaptureButtonText("Start Capture");
            showToast("Video Path : " + file.toString(), getApplicationContext());
            isRecording = false;
        }
        else {
            // initialize video camera
            if (prepareVideoRecorder()) {
                // Camera is available and unlocked, MediaRecorder is prepared,
                // now you can start recording
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                mediaRecorder.start();

                // inform the user that recording has started
                setCaptureButtonText("Stop Capture");
                isRecording = true;
            } else {
                // prepare didn't work, release the camera
                releaseMediaRecorder();
                // inform user
            }
        }
    }

    public void turnScreenOff(){
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        params.screenBrightness = .01f;
        getWindow().setAttributes(params);
    }

    public void turnScreenOn(){
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = -1f;
        getWindow().setAttributes(params);
    }


    /** Create a file Uri for saving an image or video */
    private static Uri getOutputMediaFileUri(int type){
        return Uri.fromFile(getOutputMediaFile(type));
    }

    /** Create a File for saving an image or video */
    private static File getOutputMediaFile(int type){
        // To be safe, you should check that the SDCard is
        // using Environment.getExternalStorageState() before doing this.



        // Create a media file name
        String timeStamp = new SimpleDateFormat("dd-MM-yyyy").format(new Date());
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Security Camera/" + timeStamp);
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d("Security Camera", "failed to create directory");
                return null;
            }
        }

        File mediaFile;
        timeStamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        if(type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "CAM_"+ timeStamp + ".mp4");
        } else {
            return null;
        }

        return mediaFile;
    }

    private void releaseMediaRecorder(){
        if (mediaRecorder != null) {
            mediaRecorder.reset();   // clear recorder configuration
            mediaRecorder.release(); // release the recorder object
            mediaRecorder = null;
            camera.lock();           // lock camera for later use
        }
    }

    private void releaseCamera(){
        if (camera != null){
            camera.release();        // release the camera for other applications
            camera = null;
        }
    }

    /** A safe way to get an instance of the Camera object. */
    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open();
            // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    public void setCaptureButtonText(String text) {
        captureButton.setText(text);
    }

    public void showToast(String mesasge, Context context){
        Toast.makeText(context, mesasge, Toast.LENGTH_LONG).show();
    }

}
