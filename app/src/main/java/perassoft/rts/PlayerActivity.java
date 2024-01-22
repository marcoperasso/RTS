package perassoft.rts;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class PlayerActivity extends AppCompatActivity {

    private ImageButton btnPlay;
    private ImageButton btnPause;
    private ImageButton btnStop;
    private ProgressBar pbWait;
    private TextView tvWelcome;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        btnPlay = findViewById(R.id.ibPlay);
        btnPause = findViewById(R.id.ibPause);
        btnStop = findViewById(R.id.ibStop);

        ImageButton btnInstagram = findViewById(R.id.ibInstagram);
        btnInstagram.setOnClickListener(v -> openFromUrl("https://www.instagram.com/radiotorrigliasound/"));

        ImageButton btnFacebook = findViewById(R.id.ibFacebook);
        btnFacebook.setOnClickListener(v -> openFromUrl("https://www.facebook.com/ratoso.torriglia"));

        ImageButton btnYoutube = findViewById(R.id.ibYoutube);
        btnYoutube.setOnClickListener(v -> openFromUrl("https://www.youtube.com/@radiotorrigliasound9473"));

        ImageButton btnRts = findViewById(R.id.ibRTS);
        btnRts.setOnClickListener(v -> openFromUrl("https://www.radiotorrigliasound.it/contatti"));

        pbWait = findViewById(R.id.pbWait);
        tvWelcome = findViewById(R.id.tvWelcome);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int height = displayMetrics.heightPixels;
        findViewById(R.id.ivLogo).getLayoutParams().height = (int) (height * .4);
        TextView tv = findViewById(R.id.tvCredits);
        final CharSequence text = tv.getText();
        final SpannableString spannableString = new SpannableString( text );
        spannableString.setSpan(new URLSpan(""), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tv.setText(spannableString, TextView.BufferType.SPANNABLE);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (PlayerService.isServiceRunning()) {
            showWaitMode(false);
        } else {
            startRadioService();
        }
        IntentFilter filter = new IntentFilter(PlayerService.ServiceStateMsg);
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, filter);
        updateButtons();
    }

    // Handling the received Intents for the "my-integer" event
    private final BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ((PlayerService.ServiceStateMsg.equals(action))) {
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
        if (PlayerService.isServiceRunning()) {
            Intent intent = new Intent(this, PlayerService.class);
            intent.setAction(PlayerService.ACTION_PAUSE_LISTEN);
            startService(intent);
        } else {
            startRadioService();
        }
    }
    public boolean isNetworkDown() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager != null ? connectivityManager.getActiveNetworkInfo() : null;
        return activeNetworkInfo == null || !activeNetworkInfo.isConnectedOrConnecting();
    }
    private void startRadioService() {
        if (noNetwork()) return;
        showWaitMode(true);
        btnPlay.setEnabled(false);
        btnPause.setEnabled(false);
        btnStop.setEnabled(false);

        startForegroundService(new Intent(this, PlayerService.class));
    }

    private boolean noNetwork() {
        if (isNetworkDown()) {
            Toast.makeText(this, getString(R.string.internet_not_available), Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    private void showWaitMode(boolean show) {
        int view = show ? View.VISIBLE : View.INVISIBLE;
        pbWait.setVisibility(view);
        tvWelcome.setVisibility(view);
        //findViewById(R.id.playContainer).setVisibility(show ? View.INVISIBLE : View.VISIBLE);

    }

    private void stop() {
        stopService(new Intent(this, PlayerService.class));
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
        boolean notPreparing = PlayerService.isNotServiceMediaPlayerPreparing();
        boolean playing = PlayerService.isServicePlaying();
        boolean running = PlayerService.isServiceRunning();
        showWaitMode(!notPreparing);
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

    public void goToDeveloper(View view) {
        openFromUrl("https://fuoridalsolco.com");
    }

    public void playClicked(View view) {
        playOrPause();
    }

    public void pauseClicked(View view) {
        playOrPause();
    }

    public void stopClicked(View view) {
        stop();
    }
}