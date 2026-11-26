package com.gathin.tvhome.home;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import com.gathin.tvhome.R;

import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Plays the play.json audio stream (HLS .m3u8 / mp3 / aac) in the background as a
 * foreground media service, so it keeps playing while you use other apps.
 * Stops if another app takes audio focus for good (e.g. you start Netflix).
 */
public class StreamService extends Service implements AudioManager.OnAudioFocusChangeListener {

    private static final String TAG = "StreamService";
    static final String ACTION_PLAY = "com.gathin.tvhome.stream.PLAY";
    static final String ACTION_STOP = "com.gathin.tvhome.stream.STOP";
    static final String EXTRA_URL = "url";
    static final String EXTRA_TITLE = "title";

    static final int IDLE = 0;
    static final int BUFFERING = 1;
    static final int PLAYING = 2;
    static final int ERROR = 3;

    private static final String CHANNEL = "stream";
    private static final int NOTIF_ID = 7301;
    private static final int MAX_RETRIES = 3;

    interface Listener {
        void onStreamState(int state);
    }

    private static final CopyOnWriteArraySet<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile int state = IDLE;
    private static volatile String playingUrl;

    private final Handler retryHandler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    private WifiManager.WifiLock wifiLock;
    private String url;
    private String title = StreamConfig.DEFAULT_TITLE;
    private int retries;
    private boolean pausedForFocus;

    // ------------------------------------------------------------------ static API

    static int state() {
        return state;
    }

    static boolean isActive() {
        return state == PLAYING || state == BUFFERING;
    }

    static String playingUrl() {
        return playingUrl;
    }

    static void addListener(Listener l) {
        LISTENERS.add(l);
    }

    static void removeListener(Listener l) {
        LISTENERS.remove(l);
    }

    static void play(Context c, String url, String title) {
        Intent i = new Intent(c, StreamService.class).setAction(ACTION_PLAY)
                .putExtra(EXTRA_URL, url).putExtra(EXTRA_TITLE, title);
        if (Build.VERSION.SDK_INT >= 26) {
            c.startForegroundService(i);
        } else {
            c.startService(i);
        }
    }

    static void stop(Context c) {
        if (state == IDLE || state == ERROR) {
            setState(IDLE);
        }
        c.stopService(new Intent(c, StreamService.class));
    }

    private static void setState(final int s) {
        state = s;
        MAIN.post(() -> {
            for (Listener l : LISTENERS) {
                l.onStreamState(s);
            }
        });
    }

    // ------------------------------------------------------------------ service

    @Override
    public void onCreate() {
        super.onCreate();
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action) || intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String u = intent.getStringExtra(EXTRA_URL);
        String t = intent.getStringExtra(EXTRA_TITLE);
        if (t != null) {
            title = t;
        }
        goForeground("Connecting\u2026");
        if (u == null || u.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (u.equals(url) && player != null && isActive()) {
            return START_NOT_STICKY; // already playing this stream
        }
        url = u;
        retries = 0;
        startPlayer();
        return START_NOT_STICKY;
    }

    private void startPlayer() {
        releasePlayer();
        if (!requestFocus()) {
            Log.w(TAG, "Audio focus denied");
            setState(ERROR);
            stopSelf();
            return;
        }
        setState(BUFFERING);
        playingUrl = url;
        updateNotification("Connecting\u2026");
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            player.setDataSource(url);
            player.setOnPreparedListener(mp -> {
                retries = 0;
                mp.start();
                setState(PLAYING);
                updateNotification("Playing in background");
            });
            player.setOnInfoListener((mp, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                    setState(BUFFERING);
                    updateNotification("Buffering\u2026");
                } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                    setState(PLAYING);
                    updateNotification("Playing in background");
                }
                return false;
            });
            player.setOnErrorListener((mp, what, extra) -> {
                Log.w(TAG, "Player error " + what + "/" + extra);
                retryOrFail();
                return true;
            });
            // A live stream should never "complete"; treat it as a drop and reconnect.
            player.setOnCompletionListener(mp -> retryOrFail());
            player.prepareAsync();
            acquireWifiLock();
        } catch (Exception e) {
            Log.w(TAG, "Couldn't start stream: " + e);
            retryOrFail();
        }
    }

    private void retryOrFail() {
        if (retries < MAX_RETRIES) {
            retries++;
            setState(BUFFERING);
            updateNotification("Reconnecting\u2026");
            retryHandler.postDelayed(this::startPlayer, 1500L * retries);
        } else {
            setState(ERROR);
            stopSelf();
        }
    }

    @Override
    public void onAudioFocusChange(int change) {
        if (player == null) {
            return;
        }
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
                stopSelf(); // another app started playing (e.g. Netflix) - get out of the way
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (player.isPlaying()) {
                    player.pause();
                    pausedForFocus = true;
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                player.setVolume(0.25f, 0.25f);
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                player.setVolume(1f, 1f);
                if (pausedForFocus) {
                    pausedForFocus = false;
                    player.start();
                }
                break;
            default:
                break;
        }
    }

    private boolean requestFocus() {
        if (audio == null) {
            return true;
        }
        int r;
        if (Build.VERSION.SDK_INT >= 26) {
            if (focusRequest == null) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setOnAudioFocusChangeListener(this)
                        .setWillPauseWhenDucked(false)
                        .build();
            }
            r = audio.requestAudioFocus(focusRequest);
        } else {
            r = audio.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        return r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonFocus() {
        if (audio == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            if (focusRequest != null) {
                audio.abandonAudioFocusRequest(focusRequest);
            }
        } else {
            audio.abandonAudioFocus(this);
        }
    }

    private void acquireWifiLock() {
        try {
            if (wifiLock == null) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "tvhome:stream");
                    wifiLock.setReferenceCounted(false);
                }
            }
            if (wifiLock != null && !wifiLock.isHeld()) {
                wifiLock.acquire();
            }
        } catch (Exception ignored) {
            // optional
        }
    }

    private void releasePlayer() {
        retryHandler.removeCallbacksAndMessages(null);
        if (player != null) {
            try {
                player.reset();
                player.release();
            } catch (Exception ignored) {
                // already released
            }
            player = null;
        }
    }

    @Override
    public void onDestroy() {
        releasePlayer();
        abandonFocus();
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }
        playingUrl = null;
        setState(state == ERROR ? ERROR : IDLE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ------------------------------------------------------------------ notification

    private void goForeground(String text) {
        Notification n = buildNotification(text);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIF_ID, buildNotification(text));
        }
    }

    @SuppressWarnings("deprecation")
    private Notification buildNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Background stream",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        int imm = PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), imm);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, StreamService.class).setAction(ACTION_STOP), imm);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_h_radio)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setShowWhen(false)
                .addAction(new Notification.Action.Builder((android.graphics.drawable.Icon) null, "Stop", stop).build());
        return b.build();
    }
}
