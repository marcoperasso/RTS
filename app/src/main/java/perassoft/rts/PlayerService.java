package perassoft.rts;

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
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;

public class PlayerService extends Service implements
        AudioManager.OnAudioFocusChangeListener,
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener {

    private static final String CHANNEL_ID = "RADIO_SERVICE_CHANNEL";
    private static final String NUM_RETRIES = "NUM_RETRIES";
    private static final long NETWORK_TIMEOUT = 15 * 60000;//15 minuti
    private static PlayerService serviceRunning = null;
    private static final int notificationId = 1;
    public static final String ACTION_STOP_LISTEN = "action_stop_listen";
    public static final String ACTION_PAUSE_LISTEN = "action_pause_listen";
    public static final String ACTION_RETRY_LISTEN = "action_retry_listen";
    private AudioFocusRequest mFocusRequest;
    private boolean serviceMediaPlayerPreparing = true;
    private NotificationCompat.Builder mBuilder;
    private PendingIntent pOpen;
    private PendingIntent pStop;
    private PendingIntent pPause;
    private int numRetries = 0;
    private boolean errorHandled = false;
    private boolean wasPlayingWhenNetworkDown = false;
    private boolean isPlaying;
    private ConnectivityManager.NetworkCallback mNetworkCallback;
    private boolean networkLost;
    private CountDownTimer timer;

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
            return serviceRunning != null && serviceRunning.isPlaying;
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

    private HeadsetPlugStateReceiver headsetPlugStateReceiver;
    private boolean firstTimeForHeadsetPlugStateReceiver = true;
    public static String ServiceStateMsg = "service_state";
    private AudioAttributes mPlaybackAttributes;
    private MediaPlayer mPlayer;

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        tryPlay();

        serviceMediaPlayerPreparing = false;
        sendServiceStateMessage();
    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
        if (isNetworkDown()) {
            if (serviceMediaPlayerPreparing) {
                stopServiceAndNotify(getString(R.string.internet_not_available));
            }
        } else {
            stopServiceAndNotify(getString(R.string.media_player_error));
            if (++numRetries <= 3 && !errorHandled) {
                errorHandled = true;
                restartService();
            }
        }

        return true;
    }

    private void restartService() {
        Intent intent = new Intent(this, PlayerService.class);
        intent.setAction(ACTION_RETRY_LISTEN);
        intent.putExtra(NUM_RETRIES, numRetries);
        startService(intent);
    }

    private void stopServiceAndNotify(String message) {
        if (message != null)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        serviceMediaPlayerPreparing = false;
        sendServiceStateMessage();
        stopForeground(true);
        stopSelf();
    }

    private class HeadsetPlugStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            //all'avvio viene ricevuto il messaggio anche senza che le cuffie siano state toccate
            if (firstTimeForHeadsetPlugStateReceiver) {
                firstTimeForHeadsetPlugStateReceiver = false;
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

        Intent open = new Intent(this, PlayerActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        pOpen = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

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
                .addAction(R.drawable.ic_open, getString(R.string.open), pOpen)
                .addAction(R.drawable.ic_pause, getString(R.string.pause), pPause)
                .addAction(R.drawable.ic_stop, getString(R.string.stop), pStop);
        startForeground(notificationId, mBuilder.build());

        if (isNetworkDown()) {
            stopServiceAndNotify(getString(R.string.internet_not_available));
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
            stopServiceAndNotify(e.getMessage());
            return;
        }
        mPlayer.setAudioAttributes(mPlaybackAttributes);
        mPlayer.setOnPreparedListener(this);
        mPlayer.setOnErrorListener(this);
        mPlayer.prepareAsync();
        setServiceRunning(this);

        headsetPlugStateReceiver = new HeadsetPlugStateReceiver();
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(headsetPlugStateReceiver, filter, RECEIVER_EXPORTED);

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build();
        mNetworkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                if (wasPlayingWhenNetworkDown) {
                    stopServiceAndNotify(getString(R.string.network_change_detected_restarting_service));
                    restartService();
                }
                if (timer != null)
                    timer.cancel();
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                if (serviceMediaPlayerPreparing) {
                    stopServiceAndNotify(getString(R.string.internet_not_available));
                    return;
                }
                wasPlayingWhenNetworkDown = isPlaying;
                if (wasPlayingWhenNetworkDown) {
                    timer = new CountDownTimer(NETWORK_TIMEOUT, NETWORK_TIMEOUT) {

                        public void onTick(long millisUntilFinished) {

                        }

                        public void onFinish() {
                            stopServiceAndNotify(getString(R.string.internet_not_available));
                        }
                    }.start();
                    pauseRadio();
                }
                networkLost = true;
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities);
            }
        };
        ConnectivityManager connectivityManager = getSystemService(ConnectivityManager.class);
        connectivityManager.requestNetwork(networkRequest, mNetworkCallback);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case ACTION_STOP_LISTEN:
                        stopForeground(true);
                        stopSelf();
                        return START_NOT_STICKY;
                    case ACTION_PAUSE_LISTEN:
                        if (mPlayer != null) {
                            if (isPlaying) {
                                pausePlayer();
                            } else {
                                tryPlay();
                            }
                            updateNotification();
                            sendServiceStateMessage();
                        }

                        return START_NOT_STICKY;
                    case ACTION_RETRY_LISTEN:
                        numRetries = intent.getIntExtra(NUM_RETRIES, 0);
                        break;
                }
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void updateNotification() {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        mBuilder
                .clearActions()
                .addAction(R.drawable.ic_open, getString(R.string.open), pOpen);
        if (isPlaying) {
            mBuilder
                    .setContentTitle(getString(R.string.radio_running))
                    .addAction(R.drawable.ic_pause, getString(R.string.pause), pPause);

        } else {
            mBuilder
                    .setContentTitle(getString(R.string.radio_paused))
                    .addAction(R.drawable.ic_pause, getString(R.string.resume), pPause);
        }
        mBuilder.addAction(R.drawable.ic_stop, getString(R.string.stop), pStop);
        notificationManager.notify(notificationId, mBuilder.build());
    }

    private void sendServiceStateMessage() {
        Intent intent = new Intent(ServiceStateMsg);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void pauseRadio() {
        pausePlayer();
        synchronized (mFocusLock) {
            mPlaybackDelayed = false;
            mResumeOnFocusGain = false;
        }
        updateNotification();
        sendServiceStateMessage();
    }

    private void tryPlay() {
        if (isNetworkDown()) {
            Toast.makeText(this, R.string.internet_not_available, Toast.LENGTH_SHORT).show();
            return;
        }
        //rete cambiata: riavvio il servizio altrimenti il media player va in errore e avverrebbe comunque
        if (networkLost) {
            stopServiceAndNotify(getString(R.string.network_change_detected_restarting_service));
            restartService();
            return;
        }
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
                startPlayer();
            } else if (res == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
                mPlaybackDelayed = true;
            }
        }
    }

    void startPlayer() {
        mPlayer.start();
        isPlaying = true;
    }

    void pausePlayer() {
        mPlayer.pause();
        isPlaying = false;
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
                    startPlayer();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                synchronized (mFocusLock) {
                    // this is not a transient loss, we shouldn't automatically resume for now
                    mResumeOnFocusGain = false;
                    mPlaybackDelayed = false;
                }
                pausePlayer();
                sendServiceStateMessage();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // we handle all transient losses the same way because we never duck audio books
                synchronized (mFocusLock) {
                    // we should only resume if playback was interrupted
                    mResumeOnFocusGain = isPlaying;
                    mPlaybackDelayed = false;
                }
                pausePlayer();
                sendServiceStateMessage();
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
        if (mAudioManager != null) {
            if (mFocusRequest != null)
                mAudioManager.abandonAudioFocusRequest(mFocusRequest);
        }

        ConnectivityManager connectivityManager = getSystemService(ConnectivityManager.class);
        if (mNetworkCallback != null)
            connectivityManager.unregisterNetworkCallback(mNetworkCallback);
        if (timer != null)
            timer.cancel();
        setServiceRunning(null);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.cancel(notificationId);
        if (mPlayer != null)
            mPlayer.release();
        if (headsetPlugStateReceiver != null)
            unregisterReceiver(headsetPlugStateReceiver);
        super.onDestroy();
    }
}
