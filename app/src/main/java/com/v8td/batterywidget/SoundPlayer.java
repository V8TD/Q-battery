package com.v8td.batterywidget;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.Uri;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class SoundPlayer {

    private static MediaPlayer current;

    private SoundPlayer() {
    }

    static synchronized void play(Context context) {
        Context app = context.getApplicationContext();
        stop();

        Uri uri = null;
        String saved = Prefs.soundUri(app);
        if (saved != null) {
            try {
                uri = Uri.parse(saved);
            } catch (Throwable ignored) {
            }
        }
        Uri fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if (uri == null) uri = fallback;

        if (uri != null && playUri(app, uri)) return;
        if (fallback != null && !fallback.equals(uri) && playUri(app, fallback)) return;
        beep();
    }

    static synchronized void stop() {
        if (current != null) {
            try {
                current.stop();
            } catch (Throwable ignored) {
            }
            try {
                current.release();
            } catch (Throwable ignored) {
            }
            current = null;
        }
    }

    private static boolean playUri(Context app, Uri uri) {
        final CountDownLatch done = new CountDownLatch(1);
        MediaPlayer mp = null;
        try {
            mp = new MediaPlayer();
            current = mp;
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mp.setDataSource(app, uri);
            mp.setOnCompletionListener(m -> done.countDown());
            mp.setOnErrorListener((m, what, extra) -> {
                done.countDown();
                return true;
            });
            mp.prepare();
            mp.start();
            // Держим задачу живой, пока звук доиграет, но не дольше шести секунд.
            done.await(6, TimeUnit.SECONDS);
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (mp != null) {
                try {
                    mp.release();
                } catch (Throwable ignored) {
                }
                if (current == mp) current = null;
            }
        }
    }

    private static void beep() {
        ToneGenerator tone = null;
        try {
            tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 180);
            Thread.sleep(240L);
        } catch (Throwable ignored) {
        } finally {
            if (tone != null) {
                try {
                    tone.release();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
