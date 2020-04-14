package com.cannizarro.securitycamera;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;

public class CameraListActivity extends AppCompatActivity {

    ListView listView;
    String cameraName;
    String username;
    ArrayList<String> roomList;
    MyAdapter adapter;

    TextView textView;
    ViewGroup parent;
    //RelativeLayout relativeLayout;

    ChildEventListener listener;
    FirebaseDatabase firebaseDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_list);
        Intent intent = getIntent();
        username = intent.getStringExtra("username");
        roomList = new ArrayList<>();
        firebaseDatabase = FirebaseDatabase.getInstance();

        textView = findViewById(R.id.list_header);
        parent = (ViewGroup) textView.getParent();

        attachListener();

    }

    private void attachListView() {
        if (listView == null) {
            textView.setText(R.string.list_header_populous);
            textView.setTextColor(getResources().getColor(R.color.colorOnPrimary, getResources().newTheme()));
            parent.setForegroundGravity(Gravity.CENTER_HORIZONTAL);
            //relativeLayout.setGravity(Gravity.CENTER_HORIZONTAL);
            listView = (getLayoutInflater().inflate(R.layout.my_list_view, parent, true)).findViewById(R.id.cameraList);
            adapter = new MyAdapter();
            listView.setAdapter(adapter);
            listView.setOnItemClickListener((adapterView, view, i, l) -> {
                cameraName = roomList.get(i);
                Intent intent1 = new Intent(getApplicationContext(), SurveilActivity.class)
                        .putExtra("username", username)
                        .putExtra("cameraName", cameraName);

                Log.d("Asrar Debug", cameraName + " : Roomname");

                //Roomkeys is the real room name which enables us to have multiple rooms of same name

                startActivity(intent1);
            });

        }
    }

    private void detachListView() {
        if (listView != null) {
            parent.removeView(listView);
            listView = null;
            parent.setForegroundGravity(Gravity.CENTER);
            textView.setText(R.string.list_header_empty);
            textView.setTextColor(getResources().getColor(R.color.design_default_color_error, getResources().newTheme()));
        }
    }

    private void attachListener() {

        if (listener == null) {
            listener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                    attachListView();
                    roomList.add(dataSnapshot.getKey());
                    adapter.notifyDataSetChanged();
                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                }

                @Override
                public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
                    roomList.remove(dataSnapshot.getKey());
                    adapter.notifyDataSetChanged();
                    if (roomList.isEmpty()) {
                        detachListView();
                    }
                }

                @Override
                public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            };
            firebaseDatabase.getReference(username).addChildEventListener(listener);
        }
    }


    private class MyAdapter extends BaseAdapter {

        // override other abstract methods here

        @Override
        public int getCount() {
            return roomList.size();
        }

        @Override
        public Object getItem(int i) {
            return roomList.get(i);
        }

        @Override
        public long getItemId(int i) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup container) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.list_item, container, false);
            }
            ((TextView) convertView.findViewById(R.id.item_name)).setText((CharSequence) getItem(position));
            return convertView;
        }
    }
}
