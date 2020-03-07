package com.cannizarro.securitycamera;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.webrtc.VideoTrack;

import java.io.File;

public class MediaRecorderImpl {
    private final Integer id;
    private final VideoTrack videoTrack;
    //private final AudioSamplesInterceptor audioInterceptor;
    private VideoFileRenderer videoFileRenderer;
    private boolean isRunning = false;
    private File recordFile;
    public MediaRecorderImpl(Integer id, @Nullable VideoTrack videoTrack) {
        this.id = id;
        this.videoTrack = videoTrack;
    }
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void startRecording(File file) throws Exception {
        recordFile = file;
        if (isRunning)
            return;
        isRunning = true;
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();
        if (videoTrack != null) {
            videoFileRenderer = new VideoFileRenderer(
                    file.getAbsolutePath(),
                    CameraActivity.rootEglBase.getEglBaseContext(),
                    false
            );
            videoTrack.addSink(videoFileRenderer);
        } else {
            Log.e(TAG, "Video track is null");
        }
    }
    public File getRecordFile() { return recordFile; }
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void stopRecording() {
        isRunning = false;
        if (videoTrack != null && videoFileRenderer != null) {
            videoTrack.removeSink(videoFileRenderer);
            videoFileRenderer.release();
            videoFileRenderer = null;
        }
    }


    private static final String TAG = "MediaRecorderImpl";
}