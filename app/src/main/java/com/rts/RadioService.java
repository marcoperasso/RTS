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
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;

public class RadioService extends Service implements AudioManager.OnAudioFocusChangeListener, MediaPlayer.OnPreparedListener {

    private static final String CHANNEL_ID = "RADIO_SERVICE_CHANNEL";
    private static boolean serviceRunning = false;
    private static final int notificationId = 1;
    private static final String ACTION_STOP_LISTEN = "action_stop_listen";
    private AudioFocusRequest mFocusRequest;

    private void setServiceRunning(boolean set) {
        synchronized (mRunningLock) {
            serviceRunning = set;
        }
        sendServiceStateMessage();
    }

    public static boolean isServiceRunning() {
        synchronized (mRunningLock) {
            return serviceRunning;
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
    private AudioAttributes mPlaybackAttributes;
    private MediaPlayer mPlayer;

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        playRadio();

        sendMediaReadyMessage();
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

    @Override
    public void onCreate() {
        super.onCreate();
        setServiceRunning(true);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "RTS", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("RTS channel for foreground service notification");

        Intent stopSelf = new Intent(this, RadioService.class);
        stopSelf.setAction(ACTION_STOP_LISTEN);
        PendingIntent pStopSelf = PendingIntent.getService(this, 0, stopSelf, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_radio_playing)
                .setContentTitle("Radio Torriglia Sound")
                .setSilent(true)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .addAction(R.drawable.ic_radio_playing, "Stop playing", pStopSelf);
        startForeground(notificationId, builder.build());

        mPlaybackAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();


        mPlayer = new MediaPlayer();
        try {
            mPlayer.setDataSource("https://sr7.inmystream.it/proxy/radiotor?mp=/stream");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        mPlayer.setAudioAttributes(mPlaybackAttributes);
        mPlayer.setOnPreparedListener(this);
        mPlayer.prepareAsync();
        myReceiver = new MusicIntentReceiver();
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(myReceiver, filter);

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);


    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_LISTEN.equals(intent.getAction())) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void sendServiceStateMessage() {
        Intent intent = new Intent(ServiceStateMsg);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    private void sendMediaReadyMessage() {
        Intent intent = new Intent(MediaReadyMsg);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    private void pauseRadio() {
        mPlayer.pause();
        synchronized (mFocusLock) {
            mPlaybackDelayed = false;
            mResumeOnFocusGain = false;
        }
    }

    private void playRadio() {
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
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        mAudioManager.abandonAudioFocusRequest(mFocusRequest);
        setServiceRunning(false);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.cancel(notificationId);
        mPlayer.stop();
        mPlayer.release();
        unregisterReceiver(myReceiver);
        super.onDestroy();
    }
}
