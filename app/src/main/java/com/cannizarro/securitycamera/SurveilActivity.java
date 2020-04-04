package com.cannizarro.securitycamera;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
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

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SurveilActivity extends AppCompatActivity {

    PeerConnectionFactory peerConnectionFactory;
    MediaConstraints audioConstraints;
    MediaConstraints videoConstraints;
    MediaConstraints sdpConstraints;

    SurfaceViewRenderer remoteVideoView;
    SurfaceTextureHelper surfaceTextureHelper;
    Button hangup;
    HeadsetPlugReceiver headsetPlugReceiver;
    AudioManager audioManager;

    PeerConnection localPeer;
    List<IceServer> iceServers;
    EglBase rootEglBase;

    boolean isinitiator = false;
    boolean isChannelReady = false;
    boolean isStarted = false;
    String username;
    String cameraName;
    Stack<DatabaseReference> pushedRef;
    List<PeerConnection.IceServer> peerIceServers = new ArrayList<>();

    FirebaseDatabase firebaseDatabase;
    DatabaseReference insideCameraRef;
    ChildEventListener listener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_surveil);

        if(audioManager == null)
        {
            audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        }

        setSpeakerphoneOn(true);    //Test if speaker is working without this line

        headsetPlugReceiver = new HeadsetPlugReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.HEADSET_PLUG");
        registerReceiver(headsetPlugReceiver, intentFilter);


        Intent intent = getIntent();
        username = intent.getStringExtra("username");
        cameraName = intent.getStringExtra("cameraName");

        isinitiator = false;
        firebaseDatabase = MainActivity.firebaseDatabase;

        insideCameraRef = firebaseDatabase.getReference(username + "/" + cameraName);

        isChannelReady = true;
        pushedRef = new Stack<>();
        initViews();
        initVideos();

        getIceServers();

        hangup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hangup();
            }
        });

    }

    private void initViews() {
        hangup = findViewById(R.id.end_call);
        remoteVideoView = findViewById(R.id.remote_gl_surface_view);
    }

    private void initVideos() {
        rootEglBase = EglBase.create();
        remoteVideoView.init(rootEglBase.getEglBaseContext(), null);
        remoteVideoView.setZOrderMediaOverlay(true);
    }

    private void getIceServers() {
        //get Ice servers using xirsys
        byte[] data = new byte[0];
        try {
            data = ("helloworld:ca2fa126-3095-11ea-8d0f-0242ac110003").getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
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

        audioConstraints = new MediaConstraints();
        videoConstraints = new MediaConstraints();
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());
        attachReadListener();
    }


    /**
     * This method will be called directly by the app when it is the initiator and has got the local media
     * or when the remote peer sends a message through socket that it is ready to transmit AV data
     */

    public void onTryToStart() {
        runOnUiThread(() -> {
            if (!isStarted && isChannelReady) {
                createPeerConnection();
                isStarted = true;
                if (isinitiator) {
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

            @Override
            public void onAddStream(MediaStream mediaStream) {
                showToast("Received camera stream");
                super.onAddStream(mediaStream);
                gotRemoteStream(mediaStream);
            }
        });

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
                emitMessage(sessionDescription, username);
            }
        }, sdpConstraints);
    }

    /**
     * Received remote peer's media stream. we will get the first video track and render it
     */
    private void gotRemoteStream(MediaStream stream) {
        //we have remote video stream. add to the renderer.
        final VideoTrack videoTrack = stream.videoTracks.get(0);
        runOnUiThread(() -> {
            try {
                videoTrack.addSink(remoteVideoView);
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
            if (!isinitiator && !isStarted) {
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

    @Override
    protected void onDestroy() {
        hangup();
        close();
        super.onDestroy();
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

    public void pushFun(SDP object){
        pushedRef.add(insideCameraRef.push());
        pushedRef.peek().setValue(object);
    }

    public void close() {
        isStarted = false;
        isChannelReady = false;
        detachReadListener();
        deleteEntries();
        pushedRef.clear();
        remoteVideoView.release();
        finish();
    }

    public void deleteEntries(){
        while(!pushedRef.empty())
            pushedRef.pop().setValue(null);
    }

    public void attachReadListener() {

        if (listener == null) {

            listener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                    SDP object = dataSnapshot.getValue(SDP.class);

                    if (object.username != username) {

                        Log.d("SurveilActivity", "Children added :: " + object.type);
                        String type = object.type;
                        if (type.equalsIgnoreCase("offer")) {
                            onOfferReceived(object);
                        }else if (type.equalsIgnoreCase("candidate") && isStarted) {
                            onIceCandidateReceived(object);
                        }
                    }
                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                }

                @Override
                public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {

                    showToast("Camera stopped streaming");
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

    public void showToast(final String msg) {
        runOnUiThread(() -> Toast.makeText(SurveilActivity.this, msg, Toast.LENGTH_SHORT).show());
    }


    /** Sets the speaker phone mode. */
    private void setSpeakerphoneOn(boolean on) {
        boolean wasOn = audioManager.isSpeakerphoneOn();
        if (wasOn == on) {
            return;
        }
        audioManager.setSpeakerphoneOn(on);
    }
}

