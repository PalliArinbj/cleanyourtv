package io.github.palliarinbj.spurningar;

/** The tier a run has reached: none, bronze at 5 right in a row, silver at 10, gold at 20. */
final class Progress {
    static final int NONE = 0, BRONZE = 1, SILVER = 2, GOLD = 3;
    static final int[] FROM = {0, 5, 10, 20};
    /** Each tier's own colour, for its badge, its title and its score on the leaderboard. */
    static final int[] COLOR = {0xFFFFFFFF, 0xFFD9915B, 0xFFC9D3E0, 0xFFF2C744};
    /** The background's two gradient stops for each tier, top then bottom. */
    static final int[][] BACKGROUND = {
            {0xFF2A1F4E, 0xFF120E24},
            {0xFF46291F, 0xFF170D11},
            {0xFF2B3A52, 0xFF0D121C},
            {0xFF56400F, 0xFF1A1205},
    };

    private Progress() {}

    static int tier(int score) {
        for (int t = GOLD; t > NONE; t--) if (score >= FROM[t]) return t;
        return NONE;
    }

    /** True when this score is the first in a new tier. */
    static boolean reached(int score) {
        return score > 0 && tier(score) != tier(score - 1);
    }
}
