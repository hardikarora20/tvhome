package com.gathin.tvhome.home;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import com.gathin.tvhome.R;

import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Plays the play.json audio stream in the background as a foreground media service.
 *
 * v4.1: uses Media3 ExoPlayer instead of android.media.MediaPlayer. The built-in MediaPlayer
 * has weak HLS (.m3u8) support on many TV chipsets and does not follow http -> https
 * redirects, so streams that play fine in a browser can fail there. ExoPlayer:
 *   - has a full HLS implementation (live playlists, TS/AAC/fMP4 segments, ID3),
 *   - follows cross-protocol redirects,
 *   - sends a browser-like User-Agent (some CDNs reject unknown clients),
 *   - handles audio focus itself.
 *
 * Public static API is unchanged, so HomeActivity needs no edits.
 */
@OptIn(markerClass = UnstableApi.class)
public class StreamService extends Service {

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
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 11; Android TV) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0 Safari/537.36";

    interface Listener {
        void onStreamState(int state);
    }

    private static final CopyOnWriteArraySet<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile int state = IDLE;
    private static volatile String playingUrl;
    private static volatile String lastError;

    private final Handler retryHandler = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private WifiManager.WifiLock wifiLock;
    private String url;
    private String title = StreamConfig.DEFAULT_TITLE;
    private int retries;

    // ------------------------------------------------------------------ static API (unchanged)

    static int state() {
        return state;
    }

    static boolean isActive() {
        return state == PLAYING || state == BUFFERING;
    }

    static String playingUrl() {
        return playingUrl;
    }

    /** Reason for the last failure (ExoPlayer error code + cause), or null. */
    static String lastError() {
        return lastError;
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
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        // Always enter the foreground first (required within 5 s of startForegroundService).
        goForeground("Connecting\u2026");
        if (intent == null || ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String u = intent.getStringExtra(EXTRA_URL);
        String t = intent.getStringExtra(EXTRA_TITLE);
        if (t != null) {
            title = t;
        }
        if (u == null || u.trim().isEmpty()) {
            lastError = "No url in play.json";
            setState(ERROR);
            stopSelf();
            return START_NOT_STICKY;
        }
        u = u.trim();
        if (u.equals(url) && player != null && isActive()) {
            return START_NOT_STICKY; // already playing this stream
        }
        url = u;
        retries = 0;
        lastError = null;
        startPlayer();
        return START_NOT_STICKY;
    }

    private void startPlayer() {
        releasePlayer();
        setState(BUFFERING);
        playingUrl = url;
        updateNotification("Connecting\u2026");

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(USER_AGENT)
                .setAllowCrossProtocolRedirects(true)   // http <-> https redirects
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000);

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(this).setDataSourceFactory(http))
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(), /* handleAudioFocus= */ true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setHandleAudioBecomingNoisy(true)
                .build();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_BUFFERING) {
                    setState(BUFFERING);
                    updateNotification("Buffering\u2026");
                } else if (playbackState == Player.STATE_READY) {
                    retries = 0;
                    setState(PLAYING);
                    updateNotification("Playing in background");
                } else if (playbackState == Player.STATE_ENDED) {
                    // A live stream shouldn't end; treat it as a drop and reconnect.
                    retryOrFail("Stream ended");
                }
            }

            @Override
            public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                // Another app took audio focus for good (e.g. Netflix started) -> stop.
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                    stopSelf();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                String reason = error.getErrorCodeName()
                        + (error.getCause() != null ? " (" + error.getCause().getMessage() + ")" : "");
                Log.w(TAG, "Playback error: " + reason, error);
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW && player != null) {
                    // Fell behind the live edge: jump back to live instead of failing.
                    player.seekToDefaultPosition();
                    player.prepare();
                    return;
                }
                retryOrFail(reason);
            }
        });

        MediaItem.Builder item = new MediaItem.Builder().setUri(url);
        if (url.toLowerCase().contains(".m3u8")) {
            item.setMimeType(MimeTypes.APPLICATION_M3U8);
        }
        player.setMediaItem(item.build());
        player.setPlayWhenReady(true);
        player.prepare();
        acquireWifiLock();
    }

    private void retryOrFail(String reason) {
        lastError = reason;
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
                .addAction(new Notification.Action.Builder(
                        (android.graphics.drawable.Icon) null, "Stop", stop).build());
        return b.build();
    }
}
