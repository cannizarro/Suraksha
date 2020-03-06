package com.cannizarro.securitycamera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static final String ANONYMOUS = "anonymous";
    public static final int RC_SIGN_IN= 1;
    public String username;
    public boolean hasCamera;

    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListner;

    Button camera, surveil;
    Intent intent;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        hasCamera = checkCameraHardware(getApplicationContext());


        camera = findViewById(R.id.camera);
        surveil = findViewById(R.id.surveil);

        mFirebaseAuth = FirebaseAuth.getInstance();

        mAuthStateListner = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {


                FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                if (firebaseUser != null) {
                    //signed in
                    MainActivity.this.onSignedInInitialize(firebaseUser.getDisplayName());
                } else {
                    //signed out
                    MainActivity.this.onSignedOutCleanup();
                    // Choose authentication providers
                    List<AuthUI.IdpConfig> providers = Arrays.asList(
                            new AuthUI.IdpConfig.GoogleBuilder().build());

                    // Create and launch sign-in intent
                    MainActivity.this.startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setAvailableProviders(providers)
                                    .build(),
                            RC_SIGN_IN);
                }
            }
          };

        camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(hasCamera){
                    intent = new Intent(getApplicationContext(), CameraActivity.class);
                    startActivity(intent);
                }
                else{
                    showToast("There is no camera on this device.", getApplicationContext());
                }
            }
        });
    }


    /** Check if this device has a camera */
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }


    public void signOut(){
        AuthUI.getInstance().signOut(getApplicationContext());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        switch(item.getItemId()){

            case R.id.sign_out:
                new AlertDialog.Builder(this)
                        .setIcon(R.drawable.sign_out)
                        .setTitle("Sign Out")
                        .setMessage("Are you sure?")
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                signOut();
                            }
                        })
                        .setNegativeButton("No",null)
                        .show();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode == RC_SIGN_IN)
        {
            if(resultCode == RESULT_OK)
            {
                //Signed IN
                Toast.makeText(this, "Signed In.", Toast.LENGTH_SHORT).show();

            }
            else if(resultCode == RESULT_CANCELED)
            {
                //Signed out
                Toast.makeText(this, "Exiting", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        mFirebaseAuth.addAuthStateListener(mAuthStateListner);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mAuthStateListner != null){
            mFirebaseAuth.removeAuthStateListener(mAuthStateListner);
        }
    }
    private void onSignedInInitialize(String username)
    {
        this.username=username;
    }
    private void onSignedOutCleanup()
    {
        this.username=ANONYMOUS;
    }

    public void showToast(String mesasge, Context context){
        Toast.makeText(context, mesasge, Toast.LENGTH_SHORT).show();
    }

}
