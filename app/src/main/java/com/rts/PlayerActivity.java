package com.rts;

import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageButton;

public class PlayerActivity extends AppCompatActivity implements AudioManager.OnAudioFocusChangeListener {
    MediaPlayer mPlayer;
    private ImageButton btnPlayPause;
    private boolean mPlaybackDelayed;
    private boolean mResumeOnFocusGain;
    private final Object mFocusLock = new Object();
    private AudioManager mAudioManager;
    private AudioAttributes mPlaybackAttributes;
    private MusicIntentReceiver myReceiver;
    private boolean firstTimeForMusicIntentReceiver = true;

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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        btnPlayPause = (ImageButton) findViewById(R.id.ibPlayPause);
        myReceiver = new MusicIntentReceiver();
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(myReceiver, filter);

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mPlaybackAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();


        Uri uri = Uri.parse("https://sr7.inmystream.it/proxy/radiotor?mp=/stream");
        mPlayer = MediaPlayer.create(this, uri);

        mPlayer.setAudioAttributes(mPlaybackAttributes);

        // requesting audio focus
        playRadio();

        btnPlayPause.setOnClickListener(v -> {
            if (mPlayer.isPlaying()) {
                pauseRadio();
            } else {
                playRadio();
            }
        });

    }

    private void pauseRadio() {
        mPlayer.pause();
        synchronized (mFocusLock) {
            mPlaybackDelayed = false;
            mResumeOnFocusGain = false;
        }

        updateImageButton();
    }

    private void playRadio() {
        AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(mPlaybackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(this)
                .build();
        int res = mAudioManager.requestAudioFocus(focusRequest);
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
        updateImageButton();
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
        updateImageButton();
    }

    private void updateImageButton() {
        btnPlayPause.setImageResource(mPlayer.isPlaying() ? R.drawable.pause : R.drawable.play);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        mPlayer.stop();
        unregisterReceiver(myReceiver);
        super.onDestroy();
    }
}