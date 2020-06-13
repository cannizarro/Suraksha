package com.cannizarro.securitycamera.webRTC;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class FirebaseSignalling {
    private FirebaseDatabase firebaseDatabase;
    private DatabaseReference insideCameraRef;
    private ChildEventListener listener;

    public FirebaseSignalling() {
        if(firebaseDatabase == null) {
            firebaseDatabase = FirebaseDatabase.getInstance();
        }
    }

    public void setReference(String path){
        insideCameraRef = firebaseDatabase.getReference(path);
    }

    public void deleteReference(){
        if (insideCameraRef != null)
            insideCameraRef.setValue(null);
    }

    public void push(SDP data){
        insideCameraRef.push().setValue(data);
    }

    public DatabaseReference push(){
        return insideCameraRef.push();
    }

    public void attachReadListener(SignallingInterface callback, String name) {
        if (listener == null) {
            listener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                    SDP object = dataSnapshot.getValue(SDP.class);
                    if (object != null && !object.username.equals(name)) {
                        Log.d("CameraActivity", "Children added :: " + object.toString());
                        String type = object.type;
                        if (type.equalsIgnoreCase("answer") && callback.getisStarted()) {
                            callback.onAnswerReceived(object);
                        } else if (type.equalsIgnoreCase("offer")) {
                            callback.onOfferReceived(object);
                        }
                        else if (type.equalsIgnoreCase("candidate") && callback.getisStarted()) {
                            callback.onIceCandidateReceived(object);
                        }
                    }
                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) { }

                @Override
                public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) { callback.disconnect(); }

                @Override
                public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) { }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) { }
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

    public interface SignallingInterface{

        void onAnswerReceived(SDP data);

        void onIceCandidateReceived(SDP data);

        void onOfferReceived(SDP data);

        void disconnect();

        boolean getisStarted();
    }
}
