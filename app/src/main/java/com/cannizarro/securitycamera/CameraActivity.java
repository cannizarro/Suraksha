package com.cannizarro.securitycamera;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.EglRenderer;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
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

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;



public class CameraActivity extends AppCompatActivity {

    final String TAG = "CameraActivity";
    final int ALL_PERMISSIONS_CODE = 1;
    static final int MEDIA_TYPE_VIDEO = 1;
    private boolean isRecording = false, isScreenOn=true, isInitiator=false, isChannelReady=false, isStarted=false, gotUserMedia=false;
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
    private final SparseArray<MediaRecorderImpl> mediaRecorders = new SparseArray<>();
    MediaRecorderImpl mediaRecorder;

    private File file;

    private View window;
    SurfaceViewRenderer localVideoView;
    private Button captureButton, screenOff, online;

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


        online = findViewById(R.id.online);
        captureButton = findViewById(R.id.save);
        screenOff = findViewById(R.id.screenOff);
        window = findViewById(R.id.window);

        online.setEnabled(false);

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

        online.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(isChannelReady){
                    isChannelReady=false;
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    hangup();
                    online.setText("Online");
                }
                else{
                    isChannelReady=true;
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    streamOnline();
                }
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
        if(isRecording)
            controlRecording();
        if(isStarted)
            hangup();
    }


    /**
     * Initialising Camera Preview
     */
    public void initViews(){
        localVideoView = findViewById(R.id.local_gl_surface_view);
        localVideoView.init(rootEglBase.getEglBaseContext(), null);
        localVideoView.setZOrderMediaOverlay(true);
    }

    /**
     * Method related to fulfil TURN server needs. Here we are using Xirsys
     **/
    private void getIceServers() {
        //get Ice servers using xirsys
        byte[] data = new byte[0];
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
                online.setEnabled(true);
            }

            @Override
            public void onFailure(@NonNull Call<TurnServerPojo> call, @NonNull Throwable t) {
                t.printStackTrace();
            }
        });
    }



    /**
     * Starting intitialising WebRTC built in features to be used for further streaming or video recording.
     */
    public void start(){

        if(localVideoView == null)
            initViews();

        getIceServers();

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


        //Now create a VideoCapturer instance.
        videoCapturerAndroid = createCameraCapturer(new Camera1Enumerator(false));


        //Create MediaConstraints - Will be useful for specifying video and audio constraints.
        audioConstraints = new MediaConstraints();
        videoConstraints = new MediaConstraints();

        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());

        //Create a VideoSource instance
        if (videoCapturerAndroid != null) {
            videoSource = peerConnectionFactory.createVideoSource(videoCapturerAndroid.isScreencast());
        }

        videoCapturerAndroid.initialize(surfaceTextureHelper, getApplicationContext(), videoSource.getCapturerObserver());

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

        gotUserMedia = true;

    }

    /**
     * Called when online button is pressed. Basically pushes its SDP constraints onto firebase for handshaking.
     */
    private void streamOnline(){
        // Set up the input
        final EditText input = new EditText(this);

        new AlertDialog.Builder(this)
                .setIcon(R.drawable.sign_out)
                .setTitle("Set camera name.")
                .setMessage("If you want to go online, please set this camera's name.")
                .setView(input)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        cameraName = input.getText().toString();
                        insideCameraRef = firebaseDatabase.getReference("/" + username + "/" + cameraName);
                        isInitiator = true;

                        onTryToStart();

                        attachReadListener();
                        showToast("Camera name set");
                        online.setText("Stop");
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                    }
                })
                .show();
    }

    /**
     * This method will be called directly by the app when it is the initiator and has got the local media
     * or when the remote peer sends a message through socket that it is ready to transmit AV data
     */
    public void onTryToStart() {
        runOnUiThread(() -> {
            if (!isStarted && localVideoTrack != null && isChannelReady) {
                createPeerConnection();
                isStarted = true;
                if(isInitiator){
                    doCall();
                }
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
        showToast("Streamer added");
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

    @Override
    protected void onDestroy() {
        if(isStarted)
            hangup();
        if(isRecording)
            controlRecording();

        try {
            videoCapturerAndroid.stopCapture();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        surfaceTextureHelper.stopListening();
        surfaceTextureHelper.dispose();
        localVideoView.release();
        super.onDestroy();
    }

    /**
     * Pushing ice candidates to firebase.
     */
    public void emitIceCandidate(IceCandidate iceCandidate, String username) {

        SDP object = new SDP(iceCandidate, username);
        insideCameraRef.push().setValue(object);

    }

    /**
     *Pushing the sesion description onto firebase.
     */
    public void emitMessage(SessionDescription message, String username) {

        Log.d("CameraActivity", "emitMessage() called with: message = [" + message + "]");
        SDP object = new SDP(message, username);

        insideCameraRef.push().setValue(object, new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(@Nullable DatabaseError databaseError, @NonNull DatabaseReference databaseReference) {
            }
        });
    }

    /**
     * Detaching the read listener
     */
    public void close() {
        detachReadListener();
        if(insideCameraRef!=null)
            insideCameraRef.setValue(null);
    }

    /**
     * Attaching read listener to the inside camera reference.
     */
    public void attachReadListener(){

        if(listener == null){

            listener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                    SDP object = dataSnapshot.getValue(SDP.class);

                    if(object.username != cameraName){

                        Log.d("CameraActivity", "Children added :: " + object.toString());
                        String type = object.type;
                        /*if (type.equalsIgnoreCase("offer")) {
                            onOfferReceived(object);
                        } else */if (type.equalsIgnoreCase("answer") && isStarted) {
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

                    showToast("Streamer Disconnected");
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

    public void detachReadListener(){
        if(listener != null){
            insideCameraRef.removeEventListener(listener);
            listener = null;
        }
    }

    /**
     * When this method is called:
     * if isRecording is true then recording will stop
     * else it will start recording
     */
    public void controlRecording(){
        try{
            if(isRecording){
                // stop recording and release camera
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                stopRecording(1);
                // inform the user that recording has stopped
                setCaptureButtonText("Start Capture");
                showToast("Video Path : " + file.toString());
                isRecording = false;
            }
            else {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                VideoTrack videoTrack = null;
                MediaStreamTrack track = stream.videoTracks.get(0);
                if (track instanceof VideoTrack)
                    videoTrack = (VideoTrack) track;
                file = getOutputMediaFile(MEDIA_TYPE_VIDEO);
                startRecordingToFile(file.getPath(), 1, videoTrack);

                // inform the user that recording has started
                setCaptureButtonText("Stop Capture");
                isRecording = true;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(
                    "Failed to open video file for output: ", e);
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    void startRecordingToFile(String path, Integer id, @Nullable VideoTrack videoTrack) throws Exception {

        mediaRecorder = new MediaRecorderImpl(id, videoTrack);
        mediaRecorder.startRecording(new File(path));
        mediaRecorders.append(id, mediaRecorder);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    void stopRecording(Integer id) {
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
                getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
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

    public void setCaptureButtonText(String text) {
        captureButton.setText(text);
    }

    public void showToast(final String msg) {
        runOnUiThread(() -> Toast.makeText(CameraActivity.this, msg, Toast.LENGTH_SHORT).show());
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
