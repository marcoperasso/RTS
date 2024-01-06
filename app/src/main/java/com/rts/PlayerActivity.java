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
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

public class PlayerActivity extends AppCompatActivity {

    private ImageButton btnPlay;
    private ImageButton btnPause;
    private ImageButton btnStop;
    private TextView tvWait;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        btnPlay = (ImageButton) findViewById(R.id.ibPlay);
        btnPlay.setOnClickListener(v -> playOrPause());
        btnPause = (ImageButton) findViewById(R.id.ibPause);
        btnPause.setOnClickListener(v -> playOrPause());
        btnStop = (ImageButton) findViewById(R.id.ibStop);
        btnStop.setOnClickListener(v -> stop());

        ImageButton btnInstagram = (ImageButton) findViewById(R.id.ibInstagram);
        btnInstagram.setOnClickListener(v -> openFromUrl("https://www.instagram.com/radiotorrigliasound/"));

        ImageButton btnFacebook = (ImageButton) findViewById(R.id.ibFacebook);
        btnFacebook.setOnClickListener(v -> openFromUrl("https://www.facebook.com/ratoso.torriglia"));

        ImageButton btnYoutube = (ImageButton) findViewById(R.id.ibYoutube);
        btnYoutube.setOnClickListener(v -> openFromUrl("https://www.youtube.com/@radiotorrigliasound9473"));

        tvWait = (TextView) findViewById(R.id.tvWait);

    }

    @Override
    public void onResume() {
        super.onResume();
        if (!RadioService.isServiceRunning()) {
            startRadioService();
        }
        IntentFilter filter = new IntentFilter(RadioService.ServiceStateMsg);
        filter.addAction(RadioService.MediaReadyMsg);
        filter.addAction(RadioService.PauseStateMsg);
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, filter);
        updateButtons();
    }

    // Handling the received Intents for the "my-integer" event
    private final BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (RadioService.ServiceStateMsg.equals(intent.getAction()))
                updateButtons();
            else if (RadioService.MediaReadyMsg.equals(intent.getAction())) {
                tvWait.setVisibility(View.INVISIBLE);
                updateButtons();
            } else if (intent.getAction().equals(RadioService.PauseStateMsg)) {
                updateButtons();
            }
        }
    };

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver);
        super.onPause();
    }

    private void playOrPause() {
        if (RadioService.isServiceRunning()) {
            Intent intent = new Intent(this, RadioService.class);
            intent.setAction(RadioService.ACTION_PAUSE_LISTEN);
            startService(intent);
        } else {
            startRadioService();
        }
    }

    private void startRadioService() {
        tvWait.setVisibility(View.VISIBLE);
        btnPlay.setEnabled(false);
        btnPause.setEnabled(false);
        btnStop.setEnabled(false);

        startForegroundService(new Intent(this, RadioService.class));
    }

    private void stop() {
        stopService(new Intent(this, RadioService.class));
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

    private void updateButtons() {
        boolean notPreparing = RadioService.isNotServiceMediaPlayerPreparing();
        boolean playing = RadioService.isServicePlaying();
        boolean running = RadioService.isServiceRunning();
        btnPlay.setEnabled(notPreparing);
        btnPlay.setVisibility(playing
                ? View.GONE
                : View.VISIBLE);
        btnPause.setEnabled(notPreparing);
        btnPause.setVisibility(playing
                ? View.VISIBLE
                : View.GONE);
        btnStop.setVisibility(running && notPreparing
                ? View.VISIBLE
                : View.GONE);
        btnStop.setEnabled(notPreparing);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}