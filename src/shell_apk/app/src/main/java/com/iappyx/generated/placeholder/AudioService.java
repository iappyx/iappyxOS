/*
 * MIT License
 *
 * Copyright (c) 2026 iappyx
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.iappyx.generated.placeholder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaStyleNotificationHelper;

/**
 * Foreground service for background audio playback with Media3 session support.
 * Shows lock screen controls and handles headphone/car button events.
 */
public class AudioService extends Service {
    private static final String CH = "iappyx_audio";
    private static final String TAG = "iappyxOS";
    public static final String ACTION_PLAY = "play";
    public static final String ACTION_PAUSE = "pause";
    public static final String ACTION_RESUME = "resume";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_SET_SESSION = "set_session";

    static ExoPlayer player;
    private MediaSession mediaSession;
    private String currentTitle = "Playing audio";
    private boolean stateIsPlaying = false;

    @Override
    public void onCreate() {
        super.onCreate();
        player = new ExoPlayer.Builder(this).build();
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) {
                    stateIsPlaying = false;
                    updateNotification();
                    broadcastMediaButton("complete");
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                stateIsPlaying = isPlaying;
                updateNotification();
            }

            @Override
            public void onMediaMetadataChanged(androidx.media3.common.MediaMetadata metadata) {
                if (metadata.title != null) currentTitle = metadata.title.toString();
                updateNotification();
                // Forward to ShellActivity for JS callback
                Intent intent = new Intent("com.iappyx.MEDIA_METADATA");
                intent.setPackage(getPackageName());
                String title = metadata.title != null ? metadata.title.toString() : "";
                String artist = metadata.artist != null ? metadata.artist.toString() : "";
                String station = metadata.station != null ? metadata.station.toString() : "";
                String genre = metadata.genre != null ? metadata.genre.toString() : "";
                String album = metadata.albumTitle != null ? metadata.albumTitle.toString() : "";
                intent.putExtra("title", title);
                intent.putExtra("artist", artist);
                intent.putExtra("album", album);
                intent.putExtra("station", station);
                intent.putExtra("genre", genre);
                sendBroadcast(intent);
            }
        });
        mediaSession = new MediaSession.Builder(this, player).build();
    }

    private void broadcastMediaButton(String action) {
        Intent intent = new Intent("com.iappyx.MEDIA_BUTTON");
        intent.setPackage(getPackageName());
        intent.putExtra("action", action);
        sendBroadcast(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();

        if (ACTION_STOP.equals(action)) {
            player.stop();
            player.clearMediaItems();
            stateIsPlaying = false;
            stopForeground(true);
            return START_NOT_STICKY;
        }

        if (ACTION_SET_SESSION.equals(action)) {
            currentTitle = intent.getStringExtra("title");
            if (currentTitle == null) currentTitle = "Playing audio";
            startForeground(888, buildNotification());
            return START_STICKY;
        }

        if (ACTION_PLAY.equals(action)) {
            String url = intent.getStringExtra("url");
            String title = intent.getStringExtra("title");
            boolean loop = intent.getBooleanExtra("loop", false);
            if (title != null) currentTitle = title;
            stateIsPlaying = true;
            startForeground(888, buildNotification());
            playUrl(url, loop);
            return START_STICKY;
        }

        if (ACTION_PAUSE.equals(action)) {
            player.pause();
            return START_STICKY;
        }

        if (ACTION_RESUME.equals(action)) {
            player.play();
            return START_STICKY;
        }

        if ("com.iappyx.action.PREVIOUS".equals(action)) {
            broadcastMediaButton("previous");
            return START_STICKY;
        }

        if ("com.iappyx.action.NEXT".equals(action)) {
            broadcastMediaButton("next");
            return START_STICKY;
        }

        return START_NOT_STICKY;
    }

    private void playUrl(String url, boolean loop) {
        if (url == null || url.isEmpty()) { Log.e(TAG, "AudioService: null or empty URL"); return; }
        try {
            player.stop();
            player.setMediaItem(MediaItem.fromUri(url));
            player.setRepeatMode(loop ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
            player.prepare();
            player.play();
        } catch (Exception e) {
            Log.e(TAG, "AudioService playUrl: " + e.getMessage());
        }
    }

    private void updateNotification() {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.notify(888, buildNotification());
        } catch (Exception ignored) {}
    }

    private Notification buildNotification() {
        createChannel();
        Intent launch = new Intent();
        launch.setComponent(new android.content.ComponentName(
            getPackageName(),
            "com.iappyx.generated.placeholder.ShellActivity"));
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0, launch, flags);

        // Media button actions
        PendingIntent prevPi = buildActionPi("previous", 1);
        PendingIntent playPausePi = buildActionPi(stateIsPlaying ? "pause" : "play", 2);
        PendingIntent nextPi = buildActionPi("next", 3);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(currentTitle)
            .setContentText("Tap to open app")
            .setContentIntent(pi)
            .setOngoing(true)
            .setStyle(new MediaStyleNotificationHelper.MediaStyle(mediaSession)
                .setShowActionsInCompactView(0, 1, 2));

        builder.addAction(android.R.drawable.ic_media_previous, "Previous", prevPi);
        builder.addAction(stateIsPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
            stateIsPlaying ? "Pause" : "Play", playPausePi);
        builder.addAction(android.R.drawable.ic_media_next, "Next", nextPi);

        return builder.build();
    }

    private PendingIntent buildActionPi(String action, int requestCode) {
        Intent intent = new Intent(this, AudioService.class);
        switch (action) {
            case "previous": intent.setAction("com.iappyx.action.PREVIOUS"); break;
            case "next": intent.setAction("com.iappyx.action.NEXT"); break;
            case "pause": intent.setAction(ACTION_PAUSE); break;
            case "play": intent.setAction(ACTION_RESUME); break;
        }
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(this, requestCode, intent, flags);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CH, "iappyxOS Audio", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onTaskRemoved(Intent rootIntent) {
        player.stop();
        if (mediaSession != null) mediaSession.release();
        stopForeground(true);
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        player.release();
        player = null;
        if (mediaSession != null) { mediaSession.release(); mediaSession = null; }
        super.onDestroy();
    }
}
