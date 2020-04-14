package com.cannizarro.securitycamera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.cannizarro.securitycamera.VideoRecorder.CustomVideoRecorder;
import com.cannizarro.securitycamera.webRTC.CustomPeerConnectionObserver;
import com.cannizarro.securitycamera.webRTC.CustomSdpObserver;
import com.cannizarro.securitycamera.webRTC.IceServer;
import com.cannizarro.securitycamera.webRTC.SDP;
import com.cannizarro.securitycamera.webRTC.TurnServerPojo;
import com.cannizarro.securitycamera.webRTC.Utils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.leinardi.android.speeddial.SpeedDialActionItem;
import com.leinardi.android.speeddial.SpeedDialView;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class CameraActivity extends AppCompatActivity {

    final String TAG = "CameraActivity";
    final int ALL_PERMISSIONS_CODE = 1;
    static final int MEDIA_TYPE_VIDEO = 1;
    String[] permissions = {Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE};

    private boolean isRecording = false,
            isScreenOn = true,
            isStarted = false,
            isChannelReady = false;
    private int recordingQuality = 720;
    private String username, cameraName;
    PeerConnection localPeer;
    List<IceServer> iceServers;
    List<PeerConnection.IceServer> peerIceServers = new ArrayList<>();
    PeerConnectionFactory peerConnectionFactory;
    MediaConstraints audioConstraints;
    MediaConstraints videoConstraints;
    MediaConstraints sdpConstraints;
    VideoCapturer videoCapturerAndroid;
    VideoSource videoSource;
    VideoTrack localVideoTrack;
    AudioSource audioSource;
    AudioTrack localAudioTrack;
    public static EglBase rootEglBase;
    SurfaceTextureHelper surfaceTextureHelper;
    MediaStream stream;
    CustomVideoRecorder customVideoRecorder;

    private File file;

    LinearLayout buttonGroupLayout;
    SurfaceViewRenderer localVideoView;
    SpeedDialView speedDialView;
    FloatingActionButton backButton;
    private Button captureButton, screenOffButton, onlineButton;

    FirebaseDatabase firebaseDatabase;
    DatabaseReference insideCameraRef;
    ChildEventListener listener;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        Intent intent = getIntent();
        username = intent.getStringExtra("username");

        firebaseDatabase = MainActivity.firebaseDatabase;

        rootEglBase = EglBase.create();

        onlineButton = findViewById(R.id.online);
        captureButton = findViewById(R.id.save);
        screenOffButton = findViewById(R.id.screenOff);
        localVideoView = findViewById(R.id.local_gl_surface_view);
        speedDialView = findViewById(R.id.speedDial);
        backButton = findViewById(R.id.back_button);
        buttonGroupLayout = findViewById(R.id.linearLayout);

        speedDialView.addActionItem(
                new SpeedDialActionItem.Builder(R.id.fab_action_hd, R.drawable.ic_720_hd)
                        .setFabBackgroundColor(getResources().getColor(R.color.colorSecondaryVariant, getResources().newTheme()))
                        .create());
        speedDialView.addActionItem(
                new SpeedDialActionItem.Builder(R.id.fab_action_sd, R.drawable.ic_480_sd)
                        .setFabBackgroundColor(getResources().getColor(R.color.colorSecondaryVariant, getResources().newTheme()))
                        .create());

        localVideoView.setOnClickListener(view -> {
            if (!isScreenOn) {
                turnScreenOn();
            }
        });

        screenOffButton.setOnClickListener(view -> {
            if (isScreenOn)
                turnScreenOff();
            else
                turnScreenOn();
        });

        captureButton.setOnClickListener(view -> controlRecording());

        onlineButton.setOnClickListener(view -> {
            if (isStarted) {
                hangup();
                localVideoView.setKeepScreenOn(false);
                onlineButton.setText(R.string.online);
                onlineButton.setBackgroundColor(getResources().getColor(R.color.colorPrimary, getResources().newTheme()));
            } else {
                streamOnline();
            }
        });

        speedDialView.setOnActionSelectedListener(actionItem -> {
            switch (actionItem.getId()) {
                case R.id.fab_action_hd:
                    if (videoCapturerAndroid != null && recordingQuality != 720) {
                        videoCapturerAndroid.changeCaptureFormat(1024, 720, 30);
                        recordingQuality = 720;
                        captureButton.setText("Record " + recordingQuality);
                    }
                    break;
                case R.id.fab_action_sd:
                    if (videoCapturerAndroid != null && recordingQuality != 480) {
                        videoCapturerAndroid.changeCaptureFormat(720, 480, 30);
                        recordingQuality = 480;
                        captureButton.setText("Record " + recordingQuality);
                    }
                    break;
            }

            return false;
        });

        backButton.setOnClickListener(view -> {
            onBackPressed();
        });

        if (ContextCompat.checkSelfPermission(this, permissions[0]) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, permissions[1]) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, permissions[2]) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, permissions[3]) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, permissions, ALL_PERMISSIONS_CODE);

        }

        getIceServers();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != ALL_PERMISSIONS_CODE
                || grantResults.length != 4
                || grantResults[0] != PackageManager.PERMISSION_GRANTED
                || grantResults[1] != PackageManager.PERMISSION_GRANTED
                || grantResults[2] != PackageManager.PERMISSION_GRANTED
                || grantResults[3] != PackageManager.PERMISSION_GRANTED) {

            //All permissions are not granted
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (isStarted)
            hangup();
        if (isRecording)
            controlRecording();

        if (videoCapturerAndroid != null) {
            try {
                videoCapturerAndroid.stopCapture();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            surfaceTextureHelper.stopListening();
            surfaceTextureHelper.dispose();
            localVideoView.release();
        }
        if (localVideoView == null) {
            Log.d("Asrar", "videoview is null");
        } else
            Log.d("Asrar", "videoview not null");
    }

    @Override
    protected void onResume() {
        super.onResume();
        start();
    }

    /**
     * Initialising Camera Preview
     */
    public void initRTCViews() {
        localVideoView.init(rootEglBase.getEglBaseContext(), null);
        localVideoView.setZOrderMediaOverlay(true);
    }

    /**
     * Method related to fulfil TURN server needs. Here we are using Xirsys
     **/
    private void getIceServers() {
        //get Ice servers using xirsys
        byte[] data;
        data = ("helloworld:ca2fa126-3095-11ea-8d0f-0242ac110003").getBytes(StandardCharsets.UTF_8);
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
                isChannelReady = true;
                onlineButton.setEnabled(true);
            }

            @Override
            public void onFailure(@NonNull Call<TurnServerPojo> call, @NonNull Throwable t) {
                t.printStackTrace();
                showSnackBar("Can't connect to Xirsys TURN servers. Calls over some networks won't connect", buttonGroupLayout, Snackbar.LENGTH_INDEFINITE);
                isChannelReady = true;
                onlineButton.setEnabled(true);
            }
        });
    }


    /**
     * Starting intitialising WebRTC built in features to be used for further streaming or video recording.
     */
    public void start() {

        initRTCViews();

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

        //Create MediaConstraints - Will be useful for specifying video and audio constraints.
        audioConstraints = new MediaConstraints();
        videoConstraints = new MediaConstraints();

        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());

        //Now create a VideoCapturer instance.
        videoCapturerAndroid = createCameraCapturer(new Camera1Enumerator(false));

        //Create a VideoSource instance
        if (videoCapturerAndroid != null) {
            videoSource = peerConnectionFactory.createVideoSource(videoCapturerAndroid.isScreencast());
            videoCapturerAndroid.initialize(surfaceTextureHelper, getApplicationContext(), videoSource.getCapturerObserver());
        }

        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource);

        //create an AudioSource instance
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource);

        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());

        if (videoCapturerAndroid != null) {
            videoCapturerAndroid.startCapture(1024, 720, 30);
        }

        // And finally, with our VideoRenderer ready, we
        // can add our renderer to the VideoTrack.
        localVideoTrack.addSink(localVideoView);

        stream = peerConnectionFactory.createLocalMediaStream("102");
        stream.addTrack(localVideoTrack);
    }

    /**
     * Called when online button is pressed. Basically pushes its SDP constraints onto firebase for handshaking.
     */
    private void streamOnline() {
        // Set up the input
        TextInputLayout textInputLayout = (TextInputLayout) getLayoutInflater().inflate(R.layout.my_text_input, null);
        TextInputEditText input = (TextInputEditText) textInputLayout.getEditText();

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setIcon(R.drawable.ic_security)
                .setTitle("Set camera name")
                .setMessage("If you want to go online, please set this camera's name.")
                .setView(textInputLayout)
                .setPositiveButton("OK", (dialogInterface, i) -> {
                    cameraName = input.getText().toString();
                    insideCameraRef = firebaseDatabase.getReference("/" + username + "/" + cameraName);

                    onTryToStart();

                    attachReadListener();
                    showSnackBar("Camera name set", buttonGroupLayout, Snackbar.LENGTH_LONG);
                    onlineButton.setText(R.string.stop);
                    onlineButton.setBackgroundColor(getResources().getColor(R.color.colorSecondary, getResources().newTheme()));
                    localVideoView.setKeepScreenOn(true);
                })
                .setNegativeButton("Cancel", (dialogInterface, i) -> dialogInterface.cancel());

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialog1 -> ((AlertDialog) dialog1).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false));

        // Enable Send button when there's text to send
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    (dialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                } else {
                    (dialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        dialog.show();
    }

    /**
     * This method will be called directly by the app when it is the initiator and has got the local media
     * or when the remote peer sends a message through socket that it is ready to transmit AV data
     */
    public void onTryToStart() {
        runOnUiThread(() -> {
            if (!isStarted && localVideoTrack != null) {
                createPeerConnection();
                isStarted = true;
                doCall();
            }
        });
    }

    /**
     * Creating the local peerconnection instance
     */
    private void createPeerConnection() {
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
        });

        addStreamToLocalPeer();
    }

    /**
     * Adding the stream to the localpeer
     */
    private void addStreamToLocalPeer() {
        //creating local mediastream
        MediaStream stream = peerConnectionFactory.createLocalMediaStream("102");
        stream.addTrack(localAudioTrack);
        stream.addTrack(localVideoTrack);
        localPeer.addStream(stream);
    }

    /**
     * This method is called when the app is initiator - We generate the offer and send it over through socket
     * to remote peer
     */
    private void doCall() {
        sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                "OfferToReceiveVideo", "true"));
        localPeer.createOffer(new CustomSdpObserver("localCreateOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new CustomSdpObserver("localSetLocalDesc"), sessionDescription);
                Log.d("onCreateSuccess", "SignallingClient emit ");
                emitMessage(sessionDescription, cameraName);
            }
        }, sdpConstraints);
    }

    /**
     * Received local ice candidate. Send it to remote peer through signalling for negotiation
     */
    public void onIceCandidateReceived(IceCandidate iceCandidate) {
        //we have received ice candidate. We can set it to the other peer.
        emitIceCandidate(iceCandidate, cameraName);
    }

    /**
     * Called when remote peer sends answer to your offer
     */
    public void onAnswerReceived(SDP data) {
        showSnackBar("Streamer added", buttonGroupLayout, Snackbar.LENGTH_LONG);
        localPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"), new SessionDescription(SessionDescription.Type.fromCanonicalForm(data.type.toLowerCase()), data.sdp));

    }

    /**
     * Remote IceCandidate received
     */
    public void onIceCandidateReceived(SDP data) {

        localPeer.addIceCandidate(new IceCandidate(data.id, data.label, data.candidate));

    }

    /**
     * Closes all connection and disconnects deletes all the local and online presence created.
     */
    private void hangup() {
        try {
            localPeer.close();
            localPeer = null;
            isStarted = false;
            close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * Pushing ice candidates to firebase.
     */
    public void emitIceCandidate(IceCandidate iceCandidate, String username) {

        SDP object = new SDP(iceCandidate, username);
        insideCameraRef.push().setValue(object);

    }

    /**
     * Pushing the sesion description onto firebase.
     */
    public void emitMessage(SessionDescription message, String username) {

        Log.d("CameraActivity", "emitMessage() called with: message = [" + message + "]");
        SDP object = new SDP(message, username);

        insideCameraRef.push().setValue(object, (databaseError, databaseReference) -> {
        });
    }

    /**
     * Detaching the read listener
     */
    public void close() {
        detachReadListener();
        if (insideCameraRef != null)
            insideCameraRef.setValue(null);
    }

    /**
     * Attaching read listener to the inside camera reference.
     */
    public void attachReadListener() {

        if (listener == null) {

            listener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                    SDP object = dataSnapshot.getValue(SDP.class);

                    if (object != null && !object.username.equals(cameraName)) {

                        Log.d("CameraActivity", "Children added :: " + object.toString());
                        String type = object.type;
                        if (type.equalsIgnoreCase("answer") && isStarted) {
                            onAnswerReceived(object);
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

                    showSnackBar("Streamer Disconnected", buttonGroupLayout, Snackbar.LENGTH_LONG);
                    hangup();
                    insideCameraRef = firebaseDatabase.getReference("/" + username + "/" + cameraName);
                    onTryToStart();
                    attachReadListener();
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

    /**
     * When this method is called:
     * if isRecording is true then recording will stop
     * else it will start recording
     */
    public void controlRecording() {
        try {
            if (customVideoRecorder == null) {
                customVideoRecorder = new CustomVideoRecorder();
            }
            if (isRecording) {
                // stop recording and release camera
                localVideoView.setKeepScreenOn(false);
                speedDialView.setEnabled(true);
                //getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                customVideoRecorder.stopRecording(1, getApplicationContext());
                // inform the user that recording has stopped
                captureButton.setText("Record " + recordingQuality);
                captureButton.setBackgroundColor(getResources().getColor(R.color.colorPrimary, getResources().newTheme()));
                showSnackBar(file, buttonGroupLayout, Snackbar.LENGTH_LONG);
                isRecording = false;
            } else {
                localVideoView.setKeepScreenOn(true);
                speedDialView.setEnabled(false);
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                file = getOutputMediaFile(MEDIA_TYPE_VIDEO);
                if (file != null) {
                    customVideoRecorder.startRecordingToFile(file.getPath(), 1, localVideoTrack, rootEglBase);
                }

                // inform the user that recording has started
                captureButton.setText("Stop " + recordingQuality);
                captureButton.setBackgroundColor(getResources().getColor(R.color.colorSecondary, getResources().newTheme()));
                isRecording = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(
                    "Failed to open video file for output: ", e);
        }

    }


    public void turnScreenOff() {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        params.screenBrightness = .01f;
        getWindow().setAttributes(params);
        isScreenOn = false;
        screenOffButton.setBackgroundColor(getResources().getColor(R.color.colorSecondary, getResources().newTheme()));
        captureButton.setEnabled(false);
        onlineButton.setEnabled(false);
    }

    public void turnScreenOn() {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = -1f;
        getWindow().setAttributes(params);
        screenOffButton.setBackgroundColor(getResources().getColor(R.color.colorPrimary, getResources().newTheme()));
        isScreenOn = true;
        screenOffButton.setText(R.string.dim_screen);
        captureButton.setEnabled(true);
        if (isChannelReady) {
            onlineButton.setEnabled(true);
        }
    }


    /**
     * Create a File for saving an image or video
     */
    private static File getOutputMediaFile(@SuppressWarnings("SameParameterValue") int type) {
        // To be safe, you should check that the SDCard is
        // using Environment.getExternalStorageState() before doing this.

        // Create a media file name
        String timeStamp = new SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).format(new Date());
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Security Camera/" + timeStamp);
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
        if (type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "CAM_" + timeStamp + ".mp4");
        } else {
            return null;
        }

        return mediaFile;
    }

    public void showSnackBar(String msg, View v, int length) {
        Snackbar.make(v, msg, length)
                .setAnchorView(v)
                .setTextColor(getResources().getColor(R.color.colorOnPrimary, getResources().newTheme()))
                .setBackgroundTint(getResources().getColor(R.color.material_dark_grey, getResources().newTheme()))
                .show();
    }

    public void showSnackBar(final File file, View anchorView, int length) throws IOException {
        Snackbar.make(anchorView, "Video saved. Path: " + file.getParent(), length)
                .setAction("View Video", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Respond to the click, such as by undoing the modification that caused
                        // Create the text message with a string

                        Uri selectedUri = FileProvider.getUriForFile(getApplicationContext(), getApplicationContext().getPackageName() + ".provider", file);
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(selectedUri, "video/mp4");
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        if (intent.resolveActivityInfo(getPackageManager(), 0) != null) {
                            startActivity(intent);
                        } else {
                            // if you reach this place, it means there is no any file
                            // explorer app installed on your device
                            showSnackBar("No application to view mp4 video.", anchorView, Snackbar.LENGTH_LONG);

                        }
                    }
                })
                .setAnchorView(anchorView)
                .setTextColor(getResources().getColor(R.color.colorOnPrimary, getResources().newTheme()))
                .setActionTextColor(getResources().getColor(R.color.colorSecondary, getResources().newTheme()))
                .setBackgroundTint(getResources().getColor(R.color.material_dark_grey, getResources().newTheme()))
                .show();
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find back facing camera
        Logging.d(TAG, "Looking for back facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                Logging.d(TAG, "Creating back facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        return null;
    }
}
