package com.rts;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;

public class PlayerService extends Service implements
        AudioManager.OnAudioFocusChangeListener,
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener {

    private static final String CHANNEL_ID = "RADIO_SERVICE_CHANNEL";
    private static PlayerService serviceRunning = null;
    private static final int notificationId = 1;
    public static final String ACTION_STOP_LISTEN = "action_stop_listen";
    public static final String ACTION_PAUSE_LISTEN = "action_pause_listen";
    private AudioFocusRequest mFocusRequest;
    private boolean serviceMediaPlayerPreparing = true;
    private NotificationCompat.Builder mBuilder;
    private PendingIntent pStop;
    private PendingIntent pPause;

    private void setServiceRunning(PlayerService svc) {
        synchronized (mRunningLock) {
            serviceRunning = svc;
        }
        sendServiceStateMessage();
    }

    public static boolean isServiceRunning() {
        synchronized (mRunningLock) {
            return serviceRunning != null;
        }
    }

    public static boolean isServicePlaying() {
        synchronized (mRunningLock) {
            return serviceRunning != null && serviceRunning.mPlayer.isPlaying();
        }
    }

    public static boolean isNotServiceMediaPlayerPreparing() {
        synchronized (mRunningLock) {
            return serviceRunning == null || !serviceRunning.serviceMediaPlayerPreparing;
        }
    }

    private boolean mPlaybackDelayed;
    private boolean mResumeOnFocusGain;
    private final Object mFocusLock = new Object();
    private final static Object mRunningLock = new Object();
    private AudioManager mAudioManager;

    private MusicIntentReceiver myReceiver;
    private boolean firstTimeForMusicIntentReceiver = true;
    public static String ServiceStateMsg = "service_state";
    public static String MediaReadyMsg = "media_ready";
    public static String PauseStateMsg = "pause_state";
    private AudioAttributes mPlaybackAttributes;
    private MediaPlayer mPlayer;

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        tryPlay();

        serviceMediaPlayerPreparing = false;
        sendMediaReadyMessage();
    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
        if (isNetworkDown()) {
            stopAndNotify(getString(R.string.internet_not_available));
        } else {
            stopAndNotify(getString(R.string.media_player_error));
        }
        return true;
    }

    private void stopAndNotify(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        serviceMediaPlayerPreparing = false;
        sendMediaReadyMessage();
        stopForeground(true);
        stopSelf();
    }

    private class MusicIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            //all'avvio viene ricevuto il messaggio anche senza che le cuffie siano state toccate
            if (firstTimeForMusicIntentReceiver) {
                firstTimeForMusicIntentReceiver = false;
                return;
            }
            int state = intent.getIntExtra("state", -1);
            if (state == 0)
                pauseRadio();
        }
    }

    private boolean isNetworkDown() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager != null ? connectivityManager.getActiveNetworkInfo() : null;
        return activeNetworkInfo == null || !activeNetworkInfo.isConnectedOrConnecting();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "RTS", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("RTS channel for foreground service notification");

        Intent stop = new Intent(this, PlayerService.class);
        stop.setAction(ACTION_STOP_LISTEN);
        pStop = PendingIntent.getService(this, 0, stop, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent pause = new Intent(this, PlayerService.class);
        pause.setAction(ACTION_PAUSE_LISTEN);
        pPause = PendingIntent.getService(this, 0, pause, PendingIntent.FLAG_IMMUTABLE);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);

        mBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_radio_playing)
                .setContentTitle(getString(R.string.radio_running))
                .setSilent(true)
                .setAutoCancel(false)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .addAction(R.drawable.ic_stop, getString(R.string.stop), pStop)
                .addAction(R.drawable.ic_pause, getString(R.string.pause), pPause);
        startForeground(notificationId, mBuilder.build());

        if (isNetworkDown()) {
            stopAndNotify(getString(R.string.internet_not_available));
            return;
        }
        mPlaybackAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();


        mPlayer = new MediaPlayer();
        try {
            mPlayer.setDataSource("https://sr7.inmystream.it/proxy/radiotor?mp=/stream");
        } catch (IOException e) {
            stopAndNotify(e.getMessage());
            return;
        }
        mPlayer.setAudioAttributes(mPlaybackAttributes);
        mPlayer.setOnPreparedListener(this);
        mPlayer.setOnErrorListener(this);
        mPlayer.prepareAsync();
        setServiceRunning(this);

        myReceiver = new MusicIntentReceiver();
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(myReceiver, filter);

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);


    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_STOP_LISTEN.equals(intent.getAction())) {
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            } else if (ACTION_PAUSE_LISTEN.equals(intent.getAction())) {
                if (mPlayer.isPlaying()) {
                    mPlayer.pause();
                } else {
                    tryPlay();
                }
                updateNotification();
                sendPauseStateMessage();

                return START_NOT_STICKY;
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void updateNotification() {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        mBuilder
                .clearActions()
                .addAction(R.drawable.ic_stop, getString(R.string.stop), pStop);
        if (mPlayer.isPlaying()) {
            mBuilder
                    .setContentTitle(getString(R.string.radio_running))
                    .addAction(R.drawable.ic_pause, getString(R.string.pause), pPause);

        } else {
            mBuilder
                    .setContentTitle(getString(R.string.radio_paused))
                    .addAction(R.drawable.ic_pause, getString(R.string.resume), pPause);
        }
        notificationManager.notify(notificationId, mBuilder.build());
    }

    private void sendServiceStateMessage() {
        Intent intent = new Intent(ServiceStateMsg);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void sendMediaReadyMessage() {
        Intent intent = new Intent(MediaReadyMsg);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void sendPauseStateMessage() {
        Intent intent = new Intent(PauseStateMsg);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void pauseRadio() {
        mPlayer.pause();
        synchronized (mFocusLock) {
            mPlaybackDelayed = false;
            mResumeOnFocusGain = false;
        }
        updateNotification();
    }

    private void tryPlay() {
        mFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(mPlaybackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(this)
                .build();
        int res = mAudioManager.requestAudioFocus(mFocusRequest);
        synchronized (mFocusLock) {
            if (res == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
                mPlaybackDelayed = false;
            } else if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mPlaybackDelayed = false;
                mPlayer.start();
            } else if (res == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
                mPlaybackDelayed = true;
            }
        }
    }


    // implementation of the OnAudioFocusChangeListener
    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                if (mPlaybackDelayed || mResumeOnFocusGain) {
                    synchronized (mFocusLock) {
                        mPlaybackDelayed = false;
                        mResumeOnFocusGain = false;
                    }
                    mPlayer.start();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                synchronized (mFocusLock) {
                    // this is not a transient loss, we shouldn't automatically resume for now
                    mResumeOnFocusGain = false;
                    mPlaybackDelayed = false;
                }
                mPlayer.pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // we handle all transient losses the same way because we never duck audio books
                synchronized (mFocusLock) {
                    // we should only resume if playback was interrupted
                    mResumeOnFocusGain = mPlayer.isPlaying();
                    mPlaybackDelayed = false;
                }
                mPlayer.pause();
                break;
        }
        updateNotification();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (mAudioManager != null && mFocusRequest != null)
            mAudioManager.abandonAudioFocusRequest(mFocusRequest);
        setServiceRunning(null);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.cancel(notificationId);
        if (mPlayer != null)
            mPlayer.release();
        if (myReceiver != null)
            unregisterReceiver(myReceiver);
        super.onDestroy();
    }
}
