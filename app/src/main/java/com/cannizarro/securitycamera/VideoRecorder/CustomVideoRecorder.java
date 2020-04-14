package com.cannizarro.securitycamera.VideoRecorder;

import android.content.ContentValues;
import android.content.Context;
import android.os.Build;
import android.provider.MediaStore;
import android.util.SparseArray;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.webrtc.EglBase;
import org.webrtc.VideoTrack;

import java.io.File;

public class CustomVideoRecorder {


    private final SparseArray<MediaRecorderImpl> mediaRecorders = new SparseArray<>();
    MediaRecorderImpl mediaRecorder;


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void startRecordingToFile(String path, Integer id, @Nullable VideoTrack videoTrack, EglBase rootEglBase) throws Exception {

        mediaRecorder = new MediaRecorderImpl(id, videoTrack, rootEglBase);
        mediaRecorder.startRecording(new File(path));
        mediaRecorders.append(id, mediaRecorder);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void stopRecording(Integer id, Context context) {
        MediaRecorderImpl mediaRecorder = mediaRecorders.get(id);
        if (mediaRecorder != null) {
            mediaRecorder.stopRecording();
            mediaRecorders.remove(id);
            File file = mediaRecorder.getRecordFile();
            if (file != null) {
                ContentValues values = new ContentValues(3);
                values.put(MediaStore.Video.Media.TITLE, file.getName());
                values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                values.put(MediaStore.Video.Media.DATA, file.getAbsolutePath());
                context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            }
        }
    }

}
