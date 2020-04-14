package com.cannizarro.securitycamera.VideoRecorder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.util.Log;

import java.util.Objects;


public class HeadsetPlugReceiver extends BroadcastReceiver {

    AudioManager audioManager;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Objects.equals(intent.getAction(), Intent.ACTION_HEADSET_PLUG)) {
            return;
        }

        boolean connectedHeadphones = (intent.getIntExtra("state", 0) == 1);
        // boolean connectedMicrophone = (intent.getIntExtra("microphone", 0) == 1) && connectedHeadphones;
        //String headsetName = intent.getStringExtra("name");

        if (audioManager == null) {
            audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            //noinspection ConstantConditions
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            audioManager.setMode(AudioManager.MODE_CURRENT);
        }

        if (connectedHeadphones) {
            Log.d("Hello", "connected headset");
            setSpeakerphoneOn(false);
        } else {
            Log.d("Hello", "disconnected headset");
            setSpeakerphoneOn(true);
        }

    }

    /**
     * Sets the speaker phone mode.
     */
    private void setSpeakerphoneOn(boolean on) {
        boolean wasOn = audioManager.isSpeakerphoneOn();
        if (wasOn == on) {
            return;
        }
        audioManager.setSpeakerphoneOn(on);
    }

}