package com.rts;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mPlaybackAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();


        Uri uri = Uri.parse("https://sr7.inmystream.it/proxy/radiotor?mp=/stream");
        mPlayer = MediaPlayer.create(this, uri);

        mPlayer.setAudioAttributes(mPlaybackAttributes);

        // requesting audio focus
        tryPlay();

        btnPlayPause = (ImageButton) findViewById(R.id.ibPlayPause);

        btnPlayPause.setOnClickListener(v -> {
            if (mPlayer.isPlaying()) {
                mPlayer.pause();
                synchronized (mFocusLock) {
                    mPlaybackDelayed = false;
                    mResumeOnFocusGain = false;
                }
            } else {
                tryPlay();
            }
            updateImageButton();
        });

    }

    private void tryPlay() {
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
    protected void onDestroy() {
        mPlayer.stop();

        super.onDestroy();
    }
}