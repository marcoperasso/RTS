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
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

public class PlayerActivity extends AppCompatActivity {

    private ImageButton btnPlayPause;
    private TextView tvWait;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        btnPlayPause = (ImageButton) findViewById(R.id.ibPlayPause);
        btnPlayPause.setOnClickListener(v -> playStop());

        ImageButton btnInstagram = (ImageButton) findViewById(R.id.ibInstagram);
        btnInstagram.setOnClickListener(v -> openFromUrl("https://www.instagram.com/radiotorrigliasound/"));

        ImageButton btnFacebook = (ImageButton) findViewById(R.id.ibFacebook);
        btnFacebook.setOnClickListener(v -> openFromUrl("https://www.facebook.com/ratoso.torriglia"));

        ImageButton btnYoutube = (ImageButton) findViewById(R.id.ibYoutube);
        btnYoutube.setOnClickListener(v -> openFromUrl("https://www.youtube.com/@radiotorrigliasound9473"));

        tvWait = (TextView) findViewById(R.id.tvWait);

        playStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(RadioService.ServiceStateMsg);
        filter.addAction(RadioService.MediaReadyMsg);
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, filter);
        updateImageButton();
    }

    // Handling the received Intents for the "my-integer" event
    private final BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(RadioService.ServiceStateMsg))
                updateImageButton();
            else if (intent.getAction().equals(RadioService.MediaReadyMsg)) {
                tvWait.setVisibility(View.INVISIBLE);
                btnPlayPause.setEnabled(true);
            }
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
            tvWait.setVisibility(View.VISIBLE);
            btnPlayPause.setEnabled(false);

            tvWait.setMovementMethod(new ScrollingMovementMethod());
            tvWait.animate();
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