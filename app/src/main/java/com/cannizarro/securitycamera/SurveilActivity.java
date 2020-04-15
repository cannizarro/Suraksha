package com.cannizarro.securitycamera;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.cannizarro.securitycamera.VideoRecorder.HeadsetPlugReceiver;
import com.cannizarro.securitycamera.webRTC.APIKeys;
import com.cannizarro.securitycamera.webRTC.CustomPeerConnectionObserver;
import com.cannizarro.securitycamera.webRTC.CustomSdpObserver;
import com.cannizarro.securitycamera.webRTC.IceServer;
import com.cannizarro.securitycamera.webRTC.SDP;
import com.cannizarro.securitycamera.webRTC.TurnServerPojo;
import com.cannizarro.securitycamera.webRTC.Utils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SurveilActivity extends AppCompatActivity {

    final int ALL_PERMISSIONS_CODE = 1;
    String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE};


    PeerConnectionFactory peerConnectionFactory;
    VideoTrack remoteVideoTrack;
    List<PeerConnection.IceServer> peerIceServers = new ArrayList<>();
    PeerConnection localPeer;
    List<IceServer> iceServers;
    EglBase rootEglBase;

    SurfaceViewRenderer remoteVideoView;
    FloatingActionButton backButton, captureButton;


    HeadsetPlugReceiver headsetPlugReceiver;
    IntentFilter intentFilter;
    AudioManager audioManager;
    File file;


    boolean isStarted = false;
    String username;
    String cameraName;
    Stack<DatabaseReference> pushedRef;

    FirebaseDatabase firebaseDatabase;
    DatabaseReference insideCameraRef;
    ChildEventListener listener;

    /**
     * Create a File for saving an image or video
     */
    private static File getOutputMediaFile() {
        // To be safe, you should check that the SDCard is
        // using Environment.getExternalStorageState() before doing this.

        // Create a media file name
        String timeStamp = new SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).format(new Date());
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Security Camera/" + timeStamp + "/Remote Screens/");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.
        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("Security Camera", "failed to create directory");
                return null;
            }
        }

        File mediaFile;
        timeStamp = new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH).format(new Date());
        mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                "Screen_" + timeStamp + ".png");
        return mediaFile;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_surveil);

        if (audioManager == null) {
            audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        }

        setSpeakerphoneOn();    //Test if speaker is working without this line

        headsetPlugReceiver = new HeadsetPlugReceiver();
        intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.HEADSET_PLUG");


        Intent intent = getIntent();
        username = intent.getStringExtra("username");
        cameraName = intent.getStringExtra("cameraName");

        firebaseDatabase = FirebaseDatabase.getInstance();

        insideCameraRef = firebaseDatabase.getReference(username + "/" + cameraName);

        pushedRef = new Stack<>();
        rootEglBase = EglBase.create();
        initViews();
        initVideos();

        getIceServers();


        backButton.setOnClickListener(view -> onBackPressed());
        captureButton.setOnClickListener(view -> captureFrame());

        if (ContextCompat.checkSelfPermission(this, permissions[0]) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, permissions[1]) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, permissions, ALL_PERMISSIONS_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != ALL_PERMISSIONS_CODE
                || grantResults.length != 2
                || grantResults[0] != PackageManager.PERMISSION_GRANTED
                || grantResults[1] != PackageManager.PERMISSION_GRANTED) {

            //All permissions are not granted
            finish();
        } else {
            Toast.makeText(this, "Required permissions are not granted.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(headsetPlugReceiver, intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (remoteVideoView != null) {
            remoteVideoView.setKeepScreenOn(false);
        }
        if (remoteVideoTrack != null)
            remoteVideoTrack.removeSink(remoteVideoView);
        if (remoteVideoView != null)
            remoteVideoView.release();
        unregisterReceiver(headsetPlugReceiver);
        if (isStarted)
            hangup();
    }

    private void initViews() {
        backButton = findViewById(R.id.back_button);
        captureButton = findViewById(R.id.capture_button);
        remoteVideoView = findViewById(R.id.remote_gl_surface_view);
    }

    private void initVideos() {
        remoteVideoView.init(rootEglBase.getEglBaseContext(), null);
        remoteVideoView.setZOrderMediaOverlay(true);
    }

    private void getIceServers() {
        //get Ice servers using xirsys
        byte[] data;
        data = (APIKeys.xirsys).getBytes(StandardCharsets.UTF_8);

        String authToken = "Basic " + Base64.encodeToString(data, Base64.NO_WRAP);
        Utils.getInstance().getRetrofitInstance().getIceCandidates(authToken).enqueue(new Callback<TurnServerPojo>() {
            @Override
            public void onResponse(@NonNull Call<TurnServerPojo> call, @NonNull Response<TurnServerPojo> response) {
                TurnServerPojo body = response.body();
                if (body != null) {
                    iceServers = body.iceServerList.iceServers;
                }
                for (IceServer iceServer : iceServers) {
                    if (iceServer.credential == null) {
                        PeerConnection.IceServer peerIceServer = PeerConnection.IceServer.builder(iceServer.url).createIceServer();
                        peerIceServers.add(peerIceServer);
                    } else {
                        PeerConnection.IceServer peerIceServer = PeerConnection.IceServer.builder(iceServer.url)
                                .setUsername(iceServer.username)
                                .setPassword(iceServer.credential)
                                .createIceServer();
                        peerIceServers.add(peerIceServer);
                    }
                }
                Log.d("onApiResponse", "IceServers\n" + iceServers.toString());
                start();
            }

            @Override
            public void onFailure(@NonNull Call<TurnServerPojo> call, @NonNull Throwable t) {
                t.printStackTrace();
            }
        });
    }

    public void start() {

        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        //Create a new PeerConnectionFactory instance - using Hardware encoder and decoder.
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory defaultVideoEncoderFactory = new DefaultVideoEncoderFactory(
                rootEglBase.getEglBaseContext(),  /* enableIntelVp8Encoder */true,  /* enableH264HighProfile */true);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(defaultVideoEncoderFactory)
                .setVideoDecoderFactory(defaultVideoDecoderFactory)
                .setOptions(options)
                .createPeerConnectionFactory();
        attachReadListener();
    }

    /**
     * This method will be called directly by the app when it is the initiator and has got the local media
     * or when the remote peer sends a message through socket that it is ready to transmit AV data
     */

    public void onTryToStart() {
        runOnUiThread(() -> {
            if (!isStarted) {
                createPeerConnection();
                isStarted = true;
            }
        });
    }

    /**
     * Creating the local peerconnection instance
     */
    private void createPeerConnection() {

        Log.d("onApiResponsecreatePeerConn", "IceServers\n" + peerIceServers.toString());
        PeerConnection.RTCConfiguration rtcConfig =
                new PeerConnection.RTCConfiguration(peerIceServers);
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        localPeer = peerConnectionFactory.createPeerConnection(rtcConfig, new CustomPeerConnectionObserver("localPeerCreation") {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                onIceCandidateReceived(iceCandidate);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                super.onAddStream(mediaStream);
                showSnackBar("Received camera stream", Snackbar.LENGTH_LONG);
                gotRemoteStream(mediaStream);
            }
        });

    }

    /**
     * Received remote peer's media stream. we will get the first video track and render it
     */
    private void gotRemoteStream(MediaStream stream) {
        //we have remote video stream. add to the renderer.
        remoteVideoTrack = stream.videoTracks.get(0);
        runOnUiThread(() -> {
            try {
                remoteVideoTrack.addSink(remoteVideoView);
                remoteVideoView.setKeepScreenOn(true);
                captureButton.setEnabled(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

    }

    /**
     * Received local ice candidate. Send it to remote peer through signalling for negotiation
     */
    public void onIceCandidateReceived(IceCandidate iceCandidate) {
        //we have received ice candidate. We can set it to the other peer.
        emitIceCandidate(iceCandidate, username);
    }

    /**
     * Called when remote peer sends offer
     */
    public void onOfferReceived(final SDP data) {
        runOnUiThread(() -> {
            if (!isStarted) {
                onTryToStart();
            }

            localPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"), new SessionDescription(SessionDescription.Type.OFFER, data.sdp));
            doAnswer();
        });
    }

    private void doAnswer() {
        localPeer.createAnswer(new CustomSdpObserver("localCreateAns") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new CustomSdpObserver("localSetLocal"), sessionDescription);
                emitMessage(sessionDescription, username);
            }
        }, new MediaConstraints());
    }

    /**
     * Remote IceCandidate received
     */
    public void onIceCandidateReceived(SDP data) {

        localPeer.addIceCandidate(new IceCandidate(data.id, data.label, data.candidate));

    }

    private void hangup() {
        try {
            localPeer.close();
            localPeer = null;
            close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Signalling Client Methods implemented
     */

    public void emitIceCandidate(IceCandidate iceCandidate, String username) {

        SDP object = new SDP(iceCandidate, username);
        pushFun(object);

    }

    public void emitMessage(SessionDescription message, String username) {

        SDP object = new SDP(message, username);
        pushFun(object);
    }

    public void pushFun(SDP object) {
        pushedRef.add(insideCameraRef.push());
        pushedRef.peek().setValue(object);
    }

    public void close() {
        isStarted = false;
        detachReadListener();
        deleteEntries();
        pushedRef.clear();
        remoteVideoView.release();
        finish();
    }

    public void deleteEntries() {
        while (!pushedRef.empty())
            pushedRef.pop().setValue(null);
    }

    public void attachReadListener() {

        if (listener == null) {
            listener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                    SDP object = dataSnapshot.getValue(SDP.class);

                    if (object != null && !object.username.equals(username)) {

                        Log.d("SurveilActivity", "Children added :: " + object.type);
                        String type = object.type;
                        if (type.equalsIgnoreCase("offer")) {
                            onOfferReceived(object);
                        } else if (type.equalsIgnoreCase("candidate") && isStarted) {
                            onIceCandidateReceived(object);
                        }
                    }
                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                }

                @Override
                public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
                    Toast.makeText(SurveilActivity.this, "Camera stopped streaming", Toast.LENGTH_SHORT).show();
                    hangup();
                }

                @Override
                public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                }
            };
            insideCameraRef.addChildEventListener(listener);
        }
    }

    public void detachReadListener() {
        if (listener != null) {
            insideCameraRef.removeEventListener(listener);
            listener = null;
        }
    }

    private void captureFrame() {
        remoteVideoView.addFrameListener(bitmap -> {
            file = getOutputMediaFile();
            CaptureAsync captureAsync = new CaptureAsync();
            captureAsync.execute(bitmap);
        }, 1.0f);
    }

    public void showSnackBar(String msg, int length) {
        Snackbar.make(captureButton, msg, length)
                .setTextColor(getResources().getColor(R.color.colorOnPrimary, getResources().newTheme()))
                .setBackgroundTint(getResources().getColor(R.color.material_dark_grey, getResources().newTheme()))
                .setAnchorView(captureButton)
                .show();
    }

    public void showSnackBar(final File file, int length) {
        Snackbar.make(captureButton, "Video saved. Path: " + file.getParent(), length)
                .setTextColor(getResources().getColor(R.color.colorOnPrimary, getResources().newTheme()))
                .setActionTextColor(getResources().getColor(R.color.colorSecondary, getResources().newTheme()))
                .setBackgroundTint(getResources().getColor(R.color.material_dark_grey, getResources().newTheme()))
                .setAnchorView(captureButton)
                .setAction("View Image", v -> {
                    // Respond to the click, such as by undoing the modification that caused
                    // Create the text message with a string

                    Uri selectedUri = FileProvider.getUriForFile(getApplicationContext(), getApplicationContext().getPackageName() + ".provider", file);
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(selectedUri, "image/png");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    if (intent.resolveActivityInfo(getPackageManager(), 0) != null) {
                        startActivity(intent);
                    } else {
                        // if you reach this place, it means there is no any file
                        // explorer app installed on your device
                        showSnackBar("No application to view png image.", Snackbar.LENGTH_LONG);
                    }
                })
                .show();
    }


    /**
     * Sets the speaker phone mode.
     */
    private void setSpeakerphoneOn() {
        boolean wasOn = audioManager.isSpeakerphoneOn();
        if (wasOn) {
            return;
        }
        audioManager.setSpeakerphoneOn(true);
    }

    //Creating a child of AsyncTask class named Save to run the saving the image procedure for saving each image in order of their pages
    public class CaptureAsync extends AsyncTask<Bitmap, Void, Void> {
        @Override
        protected Void doInBackground(Bitmap... bitmaps) {
            try {
                FileOutputStream outputStream = new FileOutputStream(file);
                bitmaps[0].compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            showSnackBar(file, Snackbar.LENGTH_LONG);
        }

    }
}

