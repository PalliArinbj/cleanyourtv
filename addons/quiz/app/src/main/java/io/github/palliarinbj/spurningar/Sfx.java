package io.github.palliarinbj.spurningar;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

/** The quiz's short sounds (res/raw, made by make_sounds.py), loaded once and played without delay. */
final class Sfx {
    static final int MOVE = 0, LOCK = 1, RIGHT = 2, WRONG = 3, HELPER = 4, MILESTONE = 5, FANFARE = 6, START = 7;
    private static final int[] RAW = {R.raw.sfx_move, R.raw.sfx_lock, R.raw.sfx_right, R.raw.sfx_wrong,
            R.raw.sfx_helper, R.raw.sfx_milestone, R.raw.sfx_fanfare, R.raw.sfx_start};

    private final SoundPool pool;
    private final int[] ids = new int[RAW.length];
    private int lockStream;

    Sfx(Context context) {
        pool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        for (int i = 0; i < RAW.length; i++) ids[i] = pool.load(context, RAW[i], 1);
    }

    void play(int sound) {
        int stream = pool.play(ids[sound], 1f, 1f, sound == MOVE ? 0 : 1, 0, 1f);
        if (sound == LOCK) lockStream = stream;
    }

    /** Cuts the suspense short when the answer is revealed. */
    void stopLock() {
        if (lockStream != 0) pool.stop(lockStream);
        lockStream = 0;
    }

    void release() {
        pool.release();
    }
}
