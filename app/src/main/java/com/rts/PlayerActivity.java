package com.rts;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;

public class PlayerActivity extends AppCompatActivity {

    private ImageButton btnPlayPause;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        btnPlayPause = (ImageButton) findViewById(R.id.ibPlayPause);

        ImageButton btnInstagram = (ImageButton) findViewById(R.id.ibInstagram);
        btnInstagram.setOnClickListener(v -> openFromUrl("https://www.instagram.com/radiotorrigliasound/"));
        ImageButton btnFacebook = (ImageButton) findViewById(R.id.ibFacebook);
        btnFacebook.setOnClickListener(v -> openFromUrl("https://www.facebook.com/ratoso.torriglia"));

        ImageButton btnYoutube = (ImageButton) findViewById(R.id.ibYoutube);
        btnYoutube.setOnClickListener(v -> openFromUrl("https://www.youtube.com/@radiotorrigliasound9473"));

        btnPlayPause.setOnClickListener(v -> playStop());
        updateImageButton();
        playStop();
    }
    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this) .registerReceiver(messageReceiver, new IntentFilter(RadioService.ServiceStateMsg));
    }

    // Handling the received Intents for the "my-integer" event
    private final BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateImageButton();
        }
    };

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver);
        super.onPause();
    }
    private void playStop() {
        if (RadioService.isServiceRunning()) {
            stopService(new Intent(this, RadioService.class));
        } else {
            startForegroundService(new Intent(this, RadioService.class));
        }
    }

    private void openFromUrl(String url) {
        Intent webIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse(url));
        try {
            startActivity(webIntent);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateImageButton() {
        btnPlayPause.setImageResource(RadioService.isServiceRunning() ? R.drawable.pause : R.drawable.play);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}