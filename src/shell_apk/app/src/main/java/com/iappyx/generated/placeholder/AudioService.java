package com.iappyx.generated.placeholder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.media.session.MediaButtonReceiver;

/**
 * Foreground service for background audio playback with media session support.
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

    static MediaPlayer player;
    private MediaSessionCompat mediaSession;

    @Override
    public void onCreate() {
        super.onCreate();
        mediaSession = new MediaSessionCompat(this, "iappyx_media");
        mediaSession.setActive(true);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay() { broadcastMediaButton("play"); try { if (player != null) player.start(); } catch (Exception ignored) {} updateState(true); }
            @Override public void onPause() { broadcastMediaButton("pause"); try { if (player != null) player.pause(); } catch (Exception ignored) {} updateState(false); }
            @Override public void onStop() { broadcastMediaButton("stop"); stopAudio(); updateState(false); }
            @Override public void onSkipToNext() { broadcastMediaButton("next"); }
            @Override public void onSkipToPrevious() { broadcastMediaButton("previous"); }
        });
        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_STOP | PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            .setState(PlaybackStateCompat.STATE_STOPPED, 0, 1f)
            .build());
    }

    private void broadcastMediaButton(String action) {
        Intent intent = new Intent("com.iappyx.MEDIA_BUTTON");
        intent.setPackage(getPackageName());
        intent.putExtra("action", action);
        sendBroadcast(intent);
    }

    private void updateState(boolean playing) {
        stateIsPlaying = playing;
        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_STOP | PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            .setState(playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED, 0, 1f)
            .build());
        startForeground(888, buildNotification(
            mediaSession.getController().getMetadata() != null
                ? mediaSession.getController().getMetadata().getString(MediaMetadataCompat.METADATA_KEY_TITLE)
                : "Playing audio"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        MediaButtonReceiver.handleIntent(mediaSession, intent);

        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopAudio();
            updateState(false);
            // Keep service + notification alive for quick restart — just show "paused" state
            return START_NOT_STICKY;
        }

        if (ACTION_SET_SESSION.equals(action)) {
            String title = intent.getStringExtra("title");
            String artist = intent.getStringExtra("artist");
            String album = intent.getStringExtra("album");
            MediaMetadataCompat.Builder meta = new MediaMetadataCompat.Builder();
            if (title != null) meta.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
            if (artist != null) meta.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist);
            if (album != null) meta.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album);
            mediaSession.setMetadata(meta.build());
            mediaSession.setActive(true);
            // Preserve current play state in the session + notification
            mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE |
                    PlaybackStateCompat.ACTION_STOP | PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                .setState(stateIsPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED, 0, 1f)
                .build());
            startForeground(888, buildNotification(title != null ? title : "Playing audio"));
            return START_STICKY;
        }

        if (ACTION_PLAY.equals(action)) {
            String url = intent.getStringExtra("url");
            String title = intent.getStringExtra("title");
            boolean loop = intent.getBooleanExtra("loop", false);
            stateIsPlaying = true; // Set before building notification so it shows pause button
            mediaSession.setActive(true);
            startForeground(888, buildNotification(title != null ? title : "Playing audio"));
            playUrl(url, loop);
            updateState(true);
            return START_STICKY;
        }

        if (ACTION_PAUSE.equals(action)) {
            if (player != null && player.isPlaying()) player.pause();
            updateState(false);
            return START_STICKY;
        }

        if (ACTION_RESUME.equals(action)) {
            if (player != null && !player.isPlaying()) player.start();
            updateState(true);
            return START_STICKY;
        }

        return START_NOT_STICKY;
    }

    private void playUrl(String url, boolean loop) {
        stopAudio();
        if (url == null || url.isEmpty()) { Log.e(TAG, "AudioService: null or empty URL"); return; }
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
            player.setDataSource(url);
            player.setLooping(loop);
            player.prepareAsync();
            player.setOnPreparedListener(mp -> { mp.start(); updateState(true); });
            player.setOnCompletionListener(mp -> { if (!loop) { updateState(false); broadcastMediaButton("complete"); } });
            player.setOnErrorListener((mp, w, e) -> { Log.e(TAG, "AudioService error " + w); return true; });
        } catch (Exception e) {
            Log.e(TAG, "AudioService playUrl: " + e.getMessage());
        }
    }

    private void stopAudio() {
        if (player != null) {
            try { if (player.isPlaying()) player.stop(); player.release(); }
            catch (Exception ignored) {}
            player = null;
        }
    }

    private boolean stateIsPlaying = false;

    private Notification buildNotification(String title) {
        createChannel();
        Intent launch = new Intent();
        launch.setComponent(new android.content.ComponentName(
            getPackageName(),
            "com.iappyx.generated.placeholder.ShellActivity"));
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0, launch, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText("Tap to open app")
            .setContentIntent(pi)
            .setOngoing(true)
            .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2));

        builder.addAction(android.R.drawable.ic_media_previous, "Previous",
            MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS));
        builder.addAction(stateIsPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
            stateIsPlaying ? "Pause" : "Play",
            MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                stateIsPlaying ? PlaybackStateCompat.ACTION_PAUSE : PlaybackStateCompat.ACTION_PLAY));
        builder.addAction(android.R.drawable.ic_media_next, "Next",
            MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT));

        return builder.build();
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
        // User swiped app away — stop everything
        stopAudio();
        if (mediaSession != null) { mediaSession.setActive(false); mediaSession.release(); }
        stopForeground(true);
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        stopAudio();
        if (mediaSession != null) { mediaSession.setActive(false); mediaSession.release(); }
        super.onDestroy();
    }
}
