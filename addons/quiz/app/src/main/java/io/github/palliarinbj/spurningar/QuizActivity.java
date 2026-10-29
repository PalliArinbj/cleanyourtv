package io.github.palliarinbj.spurningar;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The quiz, one activity whose screens are swapped in place: pick a difficulty, answer until the first wrong answer,
 * then the score, the right answer with its fact, and a place on the difficulty's top ten with a name.
 * Nobody picks a player first: a name is asked for only when a game makes the board.
 */
public class QuizActivity extends Activity {
    private enum Screen { START, GAME, QUIT, BREAK, OVER, NAMES, KEYBOARD, BOARDS }

    /** How long an answer glows amber before it is revealed, then how long the reveal stays. */
    private static final long LOCK_MS = 1200, RIGHT_MS = 1000, WRONG_MS = 2200;
    private static final String[] LETTERS = {"A", "B", "C", "D"};
    /** The on-screen keyboard: the Icelandic alphabet, eight to a row. */
    private static final String KEYS = "AÁBDÐEÉFGHIÍJKLMNOÓPRSTUÚVXYÝÞÆÖ";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private FrameLayout root;
    /** The screens go here; the effects layer sits over it. */
    private FrameLayout stage;
    private FxView fx;
    private Sfx sfx;
    /** Focus moved by the code, not the remote, makes no tick until then. */
    private long quietUntil;
    private ObjectAnimator pulse;
    /** The game over fanfare plays once a game, not again on Back from the name screens. */
    private boolean celebrated;
    private Store store;
    private List<Question> bank = new ArrayList<>();
    private float dp;
    private Screen screen = Screen.START;

    private Game game;
    private boolean busy;
    private View gameView;
    /** The stop prompt, the subject pick or the big question offer over the game; null when none is up. */
    private View overlayView;
    private TextView numberView, scoreView, subjectView, questionView, bannerView, tierView;
    /** The screen's gradient, recoloured as a run reaches bronze, silver and gold. */
    private GradientDrawable backdrop;
    private int backdropTier;
    private final Tile[] answers = new Tile[Round.SLOTS];
    private final Tile[] helpers = new Tile[Game.HELPERS.length];

    /** Where the last game would go on its board, -1 when it did not make it. */
    private int place = -1;
    private final StringBuilder typed = new StringBuilder();
    private TextView typedView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dp = getResources().getDisplayMetrics().density;
        store = new Store(this);
        try (InputStreamReader in = new InputStreamReader(getAssets().open("questions.txt"), StandardCharsets.UTF_8)) {
            bank = QuestionBank.parse(in);
        } catch (IOException ignored) {
        }
        root = new FrameLayout(this);
        backdrop = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, Progress.BACKGROUND[Progress.NONE]);
        root.setBackground(backdrop);
        stage = new FrameLayout(this);
        unclipped(stage);
        root.addView(stage, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        fx = new FxView(this);
        root.addView(fx, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        sfx = new Sfx(this);
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((was, now) -> {
            if (was != null && now != null && !busy && SystemClock.uptimeMillis() > quietUntil) sfx.play(Sfx.MOVE);
        });
        setContentView(root);
        showStart();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        sfx.release();
        super.onDestroy();
    }

    // ---- start -------------------------------------------------------------------------------------------------

    private void showStart() {
        screen = Screen.START;
        setBackdrop(Progress.NONE);
        LinearLayout column = column();
        column.addView(text(getString(R.string.app_name), 52, Tile.WHITE, R.font.outfit_bold));
        TextView sub = text(getString(R.string.choose_level), 22, 0xB3FFFFFF, R.font.outfit_regular);
        column.addView(sub, margins(wrap(), 0, 6, 0, 36));
        if (bank.isEmpty()) {
            column.addView(text(getString(R.string.no_questions), 22, Tile.RED, R.font.outfit_regular));
            swap(column);
            return;
        }
        LinearLayout row = row();
        Tile first = null;
        for (int level : QuestionBank.AGES) {
            Tile t = tile(levelCard(level), 0, 14);
            t.setOnClickListener(v -> startGame(level));
            row.addView(t, margins(new LinearLayout.LayoutParams(px(190), px(132)), 9, 0, 9, 0));
            if (level == store.lastLevel()) first = t;
        }
        column.addView(row);
        Tile boards = tile(getString(R.string.leaderboards), 20, 28);
        boards.setPadding(px(28), 0, px(28), 0);
        boards.setOnClickListener(v -> showBoards(0, -1));
        column.addView(boards, margins(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(56)), 0, 34, 0, 0));
        swap(column);
        if (first != null) first.requestFocus();
    }

    /** "10 ára" big, the board's best under it small. */
    private CharSequence levelCard(int level) {
        Leaderboard board = store.board(level);
        String best = board.entries().isEmpty() ? getString(R.string.no_best)
                : getString(R.string.best, board.entries().get(0).name, board.entries().get(0).score);
        String label = levelName(level);
        SpannableString s = new SpannableString(label + "\n" + best);
        s.setSpan(new RelativeSizeSpan(1.9f), 0, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new RelativeSizeSpan(0.95f), label.length() + 1, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private String levelName(int level) {
        switch (level) {
            case 7: return getString(R.string.level_7);
            case 10: return getString(R.string.level_10);
            case 13: return getString(R.string.level_13);
            default: return getString(R.string.level_16);
        }
    }

    // ---- the game ----------------------------------------------------------------------------------------------

    private void startGame(int level) {
        store.setLastLevel(level);
        game = new Game(new Deck(bank, level, store.seen(), random), level, random);
        celebrated = false;
        sfx.play(Sfx.START);
        fastForward();
        buildGameView();
        showTier(false);
        showRound();
    }

    /**
     * Debug builds only, for checking the breaks on a TV: `am start -n <app>/.QuizActivity --ei score 9 --ei helper 1`
     * and then any level starts with that many answered right and that helper used.
     */
    private void fastForward() {
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0) return;
        int score = getIntent().getIntExtra("score", 0), helper = getIntent().getIntExtra("helper", 0);
        if (helper > 0 && game.canUse(helper)) game.useHelper(helper);
        for (int i = 0; i < score && !game.over(); i++) {
            game.answer(game.round().correctSlot());
            if (i < score - 1) game.next();
        }
        if (score > 0 && !game.over()) game.next();
    }

    private void buildGameView() {
        LinearLayout column = new LinearLayout(this);
        unclipped(column);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(px(56), px(36), px(56), px(36));

        LinearLayout top = row();
        numberView = text("", 18, 0xB3FFFFFF, R.font.outfit_regular);
        numberView.setGravity(Gravity.START);
        scoreView = text("", 20, Tile.AMBER, R.font.outfit_bold);
        scoreView.setGravity(Gravity.END);
        tierView = text("", 16, Tile.DARK, R.font.outfit_bold);
        tierView.setPadding(px(14), px(4), px(14), px(4));
        tierView.setVisibility(View.INVISIBLE);
        top.addView(numberView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(tierView);
        top.addView(scoreView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        column.addView(top);

        column.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
        subjectView = text("", 16, Tile.AMBER, R.font.outfit_medium);
        subjectView.setLetterSpacing(0.12f);
        column.addView(subjectView);
        questionView = text("", 34, Tile.WHITE, R.font.outfit_medium);
        questionView.setLineSpacing(0, 1.1f);
        column.addView(questionView, margins(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT), 60, 10, 60, 0));
        column.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));

        for (int r = 0; r < 2; r++) {
            LinearLayout line = row();
            for (int c = 0; c < 2; c++) {
                int slot = r * 2 + c;
                Tile t = tile("", 24, 18);
                t.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                t.setPadding(px(26), 0, px(20), 0);
                t.setMaxLines(1);   // not setSingleLine(): autosize takes a sideways-scrolling line as endless
                // an answer of up to 40 characters, the most the question test allows, shrinks to fit
                t.setAutoSizeTextTypeUniformWithConfiguration(17, 24, 1, TypedValue.COMPLEX_UNIT_SP);
                t.setOnClickListener(v -> choose(slot));
                answers[slot] = t;
                line.addView(t, margins(new LinearLayout.LayoutParams(0, px(70), 1), 8, 8, 8, 8));
            }
            column.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        LinearLayout help = row();
        help.addView(text(getString(R.string.helpers), 18, 0xB3FFFFFF, R.font.outfit_regular),
                margins(wrap(), 0, 0, 14, 0));
        int[] descriptions = {R.string.helper_1, R.string.helper_2, R.string.helper_3};
        for (int i = 0; i < helpers.length; i++) {
            int size = Game.HELPERS[i];
            Tile t = tile("−" + size, 22, 29);
            t.setContentDescription(getString(descriptions[i]));
            t.setOnClickListener(v -> useHelper(size));
            helpers[i] = t;
            help.addView(t, margins(new LinearLayout.LayoutParams(px(58), px(58)), 6, 0, 6, 0));
        }
        LinearLayout.LayoutParams helpAt = margins(wrap(), 0, 18, 0, 0);
        helpAt.gravity = Gravity.CENTER_HORIZONTAL;
        column.addView(help, helpAt);

        // "5 í röð!" pops up over the question at every fifth right answer.
        FrameLayout frame = new FrameLayout(this);
        unclipped(frame);
        frame.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        bannerView = text("", 64, Tile.AMBER, R.font.outfit_bold);
        bannerView.setVisibility(View.INVISIBLE);
        FrameLayout.LayoutParams bannerAt = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        bannerAt.topMargin = px(120);
        frame.addView(bannerView, bannerAt);
        gameView = frame;
    }

    private void showRound() {
        screen = Screen.GAME;
        if (gameView.getParent() == null) swap(gameView);
        Round round = game.round();
        if (round == null) {
            showOver();
            return;
        }
        store.markSeen(round.question.id());
        numberView.setText(getString(R.string.question_number, levelName(game.level), game.number()));
        scoreView.setText(getString(R.string.score, game.score()));
        String subject = Subjects.name(round.question.subject);
        if (game.big()) subject = getString(R.string.big_title) + " · " + subject;
        subjectView.setText(subject.toUpperCase(java.util.Locale.ROOT));
        subjectView.setTextColor(game.big() ? Progress.COLOR[Progress.GOLD] : Tile.AMBER);
        questionView.setText(round.question.text);
        for (int slot = 0; slot < Round.SLOTS; slot++) {
            SpannableString s = new SpannableString(LETTERS[slot] + "   " + round.answer(slot));
            s.setSpan(new ForegroundColorSpan(Tile.AMBER), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            answers[slot].setText(s);
            answers[slot].setLook(Tile.Look.NORMAL);
            answers[slot].setScaleX(1f);   // a helper shrank it, a reveal popped it
            answers[slot].setScaleY(1f);
        }
        refreshHelpers();
        busy = false;
        enter();
        quietUntil = SystemClock.uptimeMillis() + 150;
        answers[0].requestFocus();
    }

    /** The question rises into place, then the answers one after another. */
    private void enter() {
        View[] parts = {subjectView, questionView, answers[0], answers[1], answers[2], answers[3]};
        for (int i = 0; i < parts.length; i++) {
            View v = parts[i];
            v.animate().cancel();
            v.setAlpha(0f);
            v.setTranslationY(px(i < 2 ? 14 : 22));
            v.animate().alpha(1f).translationY(0).setStartDelay(i < 2 ? i * 40L : 120 + (i - 2) * 60L)
                    .setDuration(260).setInterpolator(new DecelerateInterpolator(1.6f)).start();
        }
    }

    /** A helper is dimmed once used, and while the question has too few wrong answers left for it. */
    private void refreshHelpers() {
        for (int i = 0; i < helpers.length; i++) {
            int size = Game.HELPERS[i];
            helpers[i].setLook(!game.helperLeft(size) ? Tile.Look.REMOVED
                    : game.canUse(size) ? Tile.Look.NORMAL : Tile.Look.OFF);
        }
    }

    private void useHelper(int size) {
        if (busy || !game.canUse(size)) return;
        sfx.play(Sfx.HELPER);
        long delay = 0;
        for (int slot : game.useHelper(size)) {
            Tile t = answers[slot];
            t.setLook(Tile.Look.REMOVED);   // at once, so the remote cannot reach it while it breaks away
            t.setAlpha(1f);
            handler.postDelayed(() -> {
                fx.burst(t, new int[] {0xFFFFFFFF, 0xFFB8B0D8, 0xFF8E86B0}, 22, 150, true);
                t.animate().alpha(0.18f).scaleX(0.96f).scaleY(0.96f).setDuration(220).start();
            }, delay);
            delay += 110;
        }
        refreshHelpers();
        for (Tile t : answers) {
            if (t.isFocusable()) {
                t.requestFocus();
                break;
            }
        }
    }

    private void choose(int slot) {
        if (busy || game.over()) return;
        busy = true;
        Tile chosen = answers[slot];
        chosen.setLook(Tile.Look.LOCKED);
        for (Tile h : helpers) h.setLook(h.look() == Tile.Look.REMOVED ? Tile.Look.REMOVED : Tile.Look.OFF);
        sfx.play(Sfx.LOCK);
        pulse = ObjectAnimator.ofPropertyValuesHolder(chosen, PropertyValuesHolder.ofFloat(View.SCALE_X, 1.04f, 1.08f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.04f, 1.08f));
        pulse.setDuration(280);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.start();
        handler.postDelayed(() -> reveal(slot), LOCK_MS);
    }

    private void reveal(int slot) {
        int right = game.round().correctSlot();
        Tile chosen = answers[slot];
        if (pulse != null) pulse.cancel();
        sfx.stopLock();
        if (game.answer(slot) == Game.Result.RIGHT) {
            chosen.setLook(Tile.Look.RIGHT);
            sfx.play(Sfx.RIGHT);
            pop(chosen, 1.1f);
            fx.burst(chosen, new int[] {Tile.GREEN, 0xFF8AF0A8, Tile.AMBER, 0xFFFFFFFF}, 34, 260, false);
            scoreView.setText(getString(R.string.score, game.score()));
            pop(scoreView, 1.35f);
            int back = game.regained();
            if (back > 0) handler.postDelayed(() -> winBack(back), 250);
            boolean milestone = game.score() % 5 == 0;
            if (milestone) handler.postDelayed(this::milestone, back > 0 ? 1300 : 350);
            handler.postDelayed(this::afterRight, RIGHT_MS + (milestone ? 900 : 0) + (back > 0 ? 950 : 0));
        } else {
            chosen.setLook(Tile.Look.WRONG);
            chosen.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            sfx.play(Sfx.WRONG);
            shake(chosen);
            answers[right].setLook(Tile.Look.RIGHT);
            pop(answers[right], 1.06f);
            handler.postDelayed(this::showOver, WRONG_MS);
        }
    }

    /** After a right answer: the next question, or first the subject pick or the big question offer. */
    private void afterRight() {
        switch (game.pending()) {
            case SUBJECT: showSubjectPick(); break;
            case BIG: showBigOffer(); break;
            default:
                game.next();
                showRound();
        }
    }

    /** The big question answered right: the helper it won comes back with a flash of sparks. */
    private void winBack(int size) {
        Tile t = helpers[size - 1];
        refreshHelpers();
        sfx.play(Sfx.HELPER);
        pop(t, 1.5f);
        fx.burst(t, new int[] {Progress.COLOR[Progress.GOLD], Tile.AMBER, 0xFFFFFFFF}, 30, 200, false);
        banner(getString(R.string.regained, "−" + size), Progress.COLOR[Progress.GOLD], 40);
    }

    /** Five more in a row: a banner, a fanfare and confetti. A new tier says so and recolours the screen. */
    private void milestone() {
        sfx.play(Sfx.MILESTONE);
        fx.confetti(70);
        int score = game.score();
        if (Progress.reached(score)) {
            int tier = Progress.tier(score);
            showTier(true);
            banner(getString(R.string.tier_reached, tierName(tier), tierTitle(tier)), Progress.COLOR[tier], 60);
        } else {
            banner(getString(R.string.milestone, score), Tile.AMBER, 64);
        }
    }

    /** A banner over the question, on a pill of the screen's own dark colour so the question doesn't show through. */
    private void banner(CharSequence text, int color, float sp) {
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(Progress.BACKGROUND[backdropTier][1] & 0x00FFFFFF | 0xEB000000);
        pill.setStroke(px(2), color & 0x00FFFFFF | 0x80000000);
        pill.setCornerRadius(px(28));
        bannerView.setBackground(pill);
        bannerView.setIncludeFontPadding(false);
        bannerView.setPadding(px(36), px(2), px(36), px(8));
        bannerView.setText(text);
        bannerView.setTextColor(color);
        bannerView.setTextSize(sp);
        bannerView.setVisibility(View.VISIBLE);
        bannerView.setAlpha(0f);
        bannerView.setScaleX(0.4f);
        bannerView.setScaleY(0.4f);
        bannerView.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(380)
                .setInterpolator(new OvershootInterpolator(2.2f)).withEndAction(() ->
                        bannerView.animate().alpha(0f).setStartDelay(650).setDuration(300).start()).start();
    }

    /** A quick grow and settle back, with a little bounce. */
    private void pop(View v, float by) {
        v.animate().cancel();
        v.setScaleX(by);
        v.setScaleY(by);
        v.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(420)
                .setInterpolator(new OvershootInterpolator(3f)).start();
    }

    /** A head shake: no. */
    private void shake(View v) {
        float d = px(14);
        ObjectAnimator a = ObjectAnimator.ofFloat(v, View.TRANSLATION_X, 0, -d, d, -d * 0.7f, d * 0.7f,
                -d * 0.35f, d * 0.35f, 0);
        a.setDuration(520);
        a.start();
    }

    /** The tier badge beside the question number, and the screen's colour for the run's tier. */
    private void showTier(boolean animate) {
        int tier = Progress.tier(game.score());
        setBackdrop(tier);
        if (tier == Progress.NONE) {
            tierView.setVisibility(View.INVISIBLE);
            return;
        }
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(Progress.COLOR[tier]);
        pill.setCornerRadius(px(14));
        tierView.setBackground(pill);
        tierView.setText(getString(R.string.tier_badge, tierName(tier), tierTitle(tier)));
        tierView.setVisibility(View.VISIBLE);
        if (animate) pop(tierView, 1.4f);
    }

    private String tierName(int tier) {
        return getString(new int[] {0, R.string.tier_1, R.string.tier_2, R.string.tier_3}[tier]);
    }

    private String tierTitle(int tier) {
        return getString(new int[] {0, R.string.title_1, R.string.title_2, R.string.title_3}[tier]);
    }

    /** Fades the background gradient to the tier's colours. */
    private void setBackdrop(int tier) {
        if (tier == backdropTier) return;
        int[] from = Progress.BACKGROUND[backdropTier], to = Progress.BACKGROUND[tier];
        backdropTier = tier;
        ArgbEvaluator mix = new ArgbEvaluator();
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(900);
        a.addUpdateListener(an -> {
            float f = (float) an.getAnimatedValue();
            backdrop.setColors(new int[] {(int) mix.evaluate(f, from[0], to[0]), (int) mix.evaluate(f, from[1], to[1])});
        });
        a.start();
    }

    // ---- breaks in the run -------------------------------------------------------------------------------------

    /** Every fifth right answer: two subject cards flip over and the player picks the next question's subject. */
    private void showSubjectPick() {
        screen = Screen.BREAK;
        LinearLayout column = column();
        column.addView(text(getString(R.string.choose_subject), 40, Tile.WHITE, R.font.outfit_bold));
        column.addView(text(getString(R.string.choose_subject_hint), 20, 0xB3FFFFFF, R.font.outfit_regular),
                margins(wrap(), 0, 8, 0, 30));
        LinearLayout row = row();
        Tile first = null;
        int i = 0;
        for (String subject : game.subjectChoices()) {
            Tile card = tile(Subjects.name(subject), 30, 22);
            card.setOnClickListener(v -> {
                closeOverlay();
                game.next(subject);
                showRound();
            });
            row.addView(card, margins(new LinearLayout.LayoutParams(px(280), px(150)), 14, 0, 14, 0));
            card.setRotationY(90f);
            card.setAlpha(0f);
            card.animate().rotationY(0f).alpha(1f).setStartDelay(150 + i * 180L).setDuration(380)
                    .setInterpolator(new DecelerateInterpolator(1.5f)).start();
            if (first == null) first = card;
            i++;
        }
        column.addView(row);
        openOverlay(column, first);
        sfx.play(Sfx.START);
    }

    /** Every tenth, with a helper to win back: a harder question, taken up or not. */
    private void showBigOffer() {
        screen = Screen.BREAK;
        int gold = Progress.COLOR[Progress.GOLD];
        LinearLayout column = column();
        TextView title = text(getString(R.string.big_title), 56, gold, R.font.outfit_bold);
        title.setShadowLayer(px(24), 0, 0, 0x99F2C744);
        column.addView(title);
        String helper = "−" + game.toWinBack();
        String body = getString(Deck.above(game.level) > 0 ? R.string.big_text : R.string.big_text_top, helper);
        TextView text = text(body, 22, 0xE6FFFFFF, R.font.outfit_regular);
        text.setLineSpacing(0, 1.15f);
        column.addView(text, margins(wrap(), 160, 14, 160, 32));
        LinearLayout row = row();
        Tile take = button(getString(R.string.big_take), () -> {
            closeOverlay();
            game.nextBig();
            sfx.play(Sfx.LOCK);
            sfx.stopLock();
            showRound();
            // not pop(): cancelling the view's animation would leave the line faded out from enter()
            ObjectAnimator grow = ObjectAnimator.ofPropertyValuesHolder(subjectView,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1.4f, 1f), PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.4f, 1f));
            grow.setDuration(520);
            grow.setInterpolator(new OvershootInterpolator(3f));
            grow.start();
        });
        Tile skip = button(getString(R.string.big_skip), () -> {
            closeOverlay();
            game.next();
            showRound();
        });
        row.addView(take, margins(buttonSize(), 8, 0, 8, 0));
        row.addView(skip, margins(buttonSize(), 8, 0, 8, 0));
        column.addView(row);
        title.setScaleX(0.5f);
        title.setScaleY(0.5f);
        title.animate().scaleX(1f).scaleY(1f).setDuration(450).setInterpolator(new OvershootInterpolator(2.5f)).start();
        openOverlay(column, take);
        sfx.play(Sfx.MILESTONE);
    }

    /**
     * Puts a prompt over the game. The game behind is hidden and out of reach: focus could otherwise move onto an
     * answer under the prompt, and OK would answer it.
     */
    private void openOverlay(View content, View focus) {
        FrameLayout shade = new FrameLayout(this);
        shade.setBackgroundColor(0x59000000);   // the run's tier colour shows through
        shade.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        shade.setClickable(true);
        ViewGroup game = (ViewGroup) gameView;
        game.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        game.setVisibility(View.INVISIBLE);
        overlayView = shade;
        stage.addView(shade);
        shade.setAlpha(0f);
        shade.animate().alpha(1f).setDuration(200).start();
        quietUntil = SystemClock.uptimeMillis() + 150;
        if (focus != null) focus.requestFocus();
    }

    private void closeOverlay() {
        if (overlayView != null) stage.removeView(overlayView);
        overlayView = null;
        ViewGroup game = (ViewGroup) gameView;
        game.setVisibility(View.VISIBLE);
        game.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
    }

    private void showQuit() {
        screen = Screen.QUIT;
        LinearLayout column = column();
        column.addView(text(getString(R.string.quit_question), 34, Tile.WHITE, R.font.outfit_bold),
                margins(wrap(), 0, 0, 0, 28));
        LinearLayout row = row();
        Tile keep = button(getString(R.string.keep_playing), this::closeQuit);
        Tile quit = button(getString(R.string.quit), this::showStart);
        row.addView(keep, margins(buttonSize(), 8, 0, 8, 0));
        row.addView(quit, margins(buttonSize(), 8, 0, 8, 0));
        column.addView(row);
        openOverlay(column, keep);
    }

    private void closeQuit() {
        closeOverlay();
        screen = Screen.GAME;
        for (Tile t : answers) {
            if (t.isFocusable()) {
                t.requestFocus();
                break;
            }
        }
    }

    // ---- game over ---------------------------------------------------------------------------------------------

    private void showOver() {
        screen = Screen.OVER;
        place = store.board(game.level).place(game.score(), game.helpersUsed(), System.currentTimeMillis());
        LinearLayout column = column();
        column.addView(text(getString(R.string.game_over), 26, 0xB3FFFFFF, R.font.outfit_medium));
        TextView score = text(String.valueOf(game.score()), 96, Tile.AMBER, R.font.outfit_bold);
        column.addView(score);
        column.addView(text(getString(R.string.in_a_row), 22, Tile.WHITE, R.font.outfit_regular),
                margins(wrap(), 0, -6, 0, 24));
        int tier = Progress.tier(game.score());
        if (tier > Progress.NONE) {
            column.addView(text(getString(R.string.tier_badge, tierName(tier), tierTitle(tier)), 24, Progress.COLOR[tier],
                    R.font.outfit_bold), margins(wrap(), 0, -14, 0, 20));
        }
        if (game.outOfQuestions()) {
            column.addView(text(getString(R.string.out_of_questions), 22, Tile.GREEN, R.font.outfit_medium));
        } else if (game.round() != null) {
            Question q = game.round().question;
            column.addView(text(getString(R.string.the_answer, q.correct()), 24, Tile.GREEN, R.font.outfit_medium));
            if (!q.fact.isEmpty()) {
                column.addView(text(q.fact, 20, 0xCCFFFFFF, R.font.outfit_regular), margins(wrap(), 120, 8, 120, 0));
            }
        }
        Tile first;
        LinearLayout row = row();
        if (place >= 0) {
            column.addView(text(getString(R.string.made_board, place + 1), 24, Tile.WHITE, R.font.outfit_bold),
                    margins(wrap(), 0, 26, 0, 0));
            first = button(getString(R.string.save_name), this::showNames);
            row.addView(first, margins(buttonSize(), 8, 0, 8, 0));
        } else {
            first = button(getString(R.string.play_again), () -> startGame(game.level));
            row.addView(first, margins(buttonSize(), 8, 0, 8, 0));
            row.addView(button(getString(R.string.other_level), this::showStart), margins(buttonSize(), 8, 0, 8, 0));
            row.addView(button(getString(R.string.leaderboards), () -> showBoards(game.level, -1)),
                    margins(buttonSize(), 8, 0, 8, 0));
        }
        column.addView(row, margins(wrap(), 0, 30, 0, 0));
        swap(column);
        quietUntil = SystemClock.uptimeMillis() + 150;
        first.requestFocus();
        if (!celebrated) {
            celebrated = true;
            countUp(score, game.score());
            if (place >= 0) {
                handler.postDelayed(() -> {
                    sfx.play(Sfx.FANFARE);
                    fx.confetti(110);
                }, 450);
            }
        }
    }

    /** The score counts up from 0, quickly. */
    private void countUp(TextView view, int to) {
        if (to <= 0) return;
        ValueAnimator a = ValueAnimator.ofInt(0, to);
        a.setDuration(Math.min(1200, 300 + to * 60L));
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(an -> view.setText(String.valueOf(an.getAnimatedValue())));
        a.start();
    }

    // ---- a name for the board ----------------------------------------------------------------------------------

    private void showNames() {
        List<String> names = store.names();
        if (names.isEmpty()) {
            showKeyboard();
            return;
        }
        screen = Screen.NAMES;
        LinearLayout column = column();
        column.addView(text(getString(R.string.who_are_you), 40, Tile.WHITE, R.font.outfit_bold),
                margins(wrap(), 0, 0, 0, 28));
        LinearLayout row = null;
        Tile first = null;
        List<String> chips = new ArrayList<>(names);
        chips.add(null);   // "New name"
        for (int i = 0; i < chips.size(); i++) {
            if (i % 5 == 0) {
                row = row();
                column.addView(row, margins(wrap(), 0, 6, 0, 6));
            }
            String name = chips.get(i);
            Tile t = name == null ? button(getString(R.string.new_name), this::showKeyboard)
                    : button(name, () -> saveName(name));
            row.addView(t, margins(new LinearLayout.LayoutParams(px(170), px(60)), 7, 0, 7, 0));
            if (first == null) first = t;
        }
        swap(column);
        first.requestFocus();
    }

    private void showKeyboard() {
        screen = Screen.KEYBOARD;
        typed.setLength(0);
        LinearLayout column = column();
        column.addView(text(getString(R.string.type_name), 26, 0xB3FFFFFF, R.font.outfit_medium));
        typedView = text("", 44, Tile.WHITE, R.font.outfit_bold);
        typedView.setMinWidth(px(360));
        GradientDrawable line = new GradientDrawable();
        line.setColor(0x14FFFFFF);
        line.setCornerRadius(px(12));
        typedView.setBackground(line);
        typedView.setPadding(px(24), px(6), px(24), px(6));
        column.addView(typedView, margins(wrap(), 0, 12, 0, 22));
        Tile first = null;
        LinearLayout row = null;
        for (int i = 0; i < KEYS.length(); i++) {
            if (i % 8 == 0) {
                row = row();
                column.addView(row);
            }
            String key = String.valueOf(KEYS.charAt(i));
            Tile t = tile(key, 24, 12);
            t.setOnClickListener(v -> type(key));
            row.addView(t, margins(new LinearLayout.LayoutParams(px(58), px(54)), 4, 4, 4, 4));
            if (first == null) first = t;
        }
        LinearLayout last = row();
        Tile space = tile(getString(R.string.space), 20, 12);
        space.setOnClickListener(v -> type(" "));
        Tile delete = tile(getString(R.string.delete), 20, 12);
        delete.setOnClickListener(v -> {
            if (typed.length() > 0) typed.setLength(typed.length() - 1);
            showTyped();
        });
        Tile done = tile(getString(R.string.done), 20, 12);
        done.setOnClickListener(v -> {
            String name = Leaderboard.cleanName(typed.toString());
            if (!name.isEmpty()) saveName(name);
        });
        last.addView(space, margins(new LinearLayout.LayoutParams(px(190), px(54)), 4, 4, 4, 4));
        last.addView(delete, margins(new LinearLayout.LayoutParams(px(124), px(54)), 4, 4, 4, 4));
        last.addView(done, margins(new LinearLayout.LayoutParams(px(190), px(54)), 4, 4, 4, 4));
        column.addView(last);
        swap(column);
        showTyped();
        first.requestFocus();
    }

    private void type(String key) {
        if (typed.length() >= Leaderboard.MAX_NAME) return;
        if (key.equals(" ") && (typed.length() == 0 || typed.charAt(typed.length() - 1) == ' ')) return;
        // Capital first letter of each word, the rest small: "PÁLL" typed on the grid reads Páll.
        boolean wordStart = typed.length() == 0 || typed.charAt(typed.length() - 1) == ' ';
        typed.append(wordStart ? key : key.toLowerCase(new java.util.Locale("is")));
        showTyped();
    }

    private void showTyped() {
        typedView.setText(typed.length() == 0 ? "_" : typed + "_");
    }

    private void saveName(String name) {
        Leaderboard board = store.board(game.level);
        int at = board.add(name, game.score(), game.helpersUsed(), System.currentTimeMillis());
        store.saveBoard(game.level, board);
        store.useName(Leaderboard.cleanName(name));
        showBoards(game.level, at);
    }

    // ---- leaderboards ------------------------------------------------------------------------------------------

    /** All four boards side by side; the given place on the given board is marked as just added. */
    private void showBoards(int level, int newPlace) {
        screen = Screen.BOARDS;
        LinearLayout column = column();
        column.addView(text(getString(R.string.leaderboards), 36, Tile.WHITE, R.font.outfit_bold),
                margins(wrap(), 0, 0, 0, 16));
        LinearLayout boards = row();
        boards.setGravity(Gravity.TOP);
        for (int age : QuestionBank.AGES) {
            LinearLayout list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x14FFFFFF);
            bg.setCornerRadius(px(16));
            list.setBackground(bg);
            list.setPadding(px(16), px(12), px(16), px(12));
            TextView head = text(levelName(age), 22, Tile.AMBER, R.font.outfit_bold);
            list.addView(head, margins(wrap(), 0, 0, 0, 8));
            List<Leaderboard.Entry> entries = store.board(age).entries();
            if (entries.isEmpty()) list.addView(text(getString(R.string.nobody_yet), 16, 0x99FFFFFF, R.font.outfit_regular));
            for (int i = 0; i < entries.size(); i++) {
                Leaderboard.Entry e = entries.get(i);
                boolean fresh = age == level && i == newPlace;
                LinearLayout line = new LinearLayout(this);
                line.setPadding(px(8), px(3), px(8), px(3));
                if (fresh) {
                    GradientDrawable mark = new GradientDrawable();
                    mark.setColor(Tile.AMBER);
                    mark.setCornerRadius(px(8));
                    line.setBackground(mark);
                }
                int color = fresh ? Tile.DARK : Tile.WHITE;
                TextView rank = text((i + 1) + ".", 17, fresh ? Tile.DARK : 0x99FFFFFF, R.font.outfit_regular);
                rank.setGravity(Gravity.END);
                line.addView(rank, new LinearLayout.LayoutParams(px(28), ViewGroup.LayoutParams.WRAP_CONTENT));
                TextView name = text(e.name, 17, color, R.font.outfit_medium);
                name.setGravity(Gravity.START);
                name.setSingleLine(true);
                line.addView(name, margins(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1), 10, 0, 6, 0));
                line.addView(text(String.valueOf(e.score), 17, fresh ? Tile.DARK : Progress.COLOR[Progress.tier(e.score)],
                        R.font.outfit_bold));
                list.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            boards.addView(list, margins(new LinearLayout.LayoutParams(px(200), px(292)), 8, 0, 8, 0));
        }
        column.addView(boards);
        Tile play = button(getString(R.string.play), this::showStart);
        column.addView(play, margins(buttonSize(), 0, 20, 0, 0));
        swap(column);
        play.requestFocus();
        if (newPlace >= 0) fx.confetti(60);
    }

    // ---- keys --------------------------------------------------------------------------------------------------

    @Override
    public void onBackPressed() {
        switch (screen) {
            case START: super.onBackPressed(); break;
            case GAME: if (!busy) showQuit(); break;
            case QUIT: closeQuit(); break;
            case BREAK: break;   // a choice is waiting: Back must not throw the run away
            case NAMES: showOver(); break;
            case KEYBOARD: if (store.names().isEmpty()) showOver(); else showNames(); break;
            default: showStart();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // While an answer is locked in or revealed, the remote does nothing but Back.
        if (screen == Screen.GAME && busy && keyCode != KeyEvent.KEYCODE_BACK) return true;
        return super.onKeyDown(keyCode, event);
    }

    // ---- building blocks ---------------------------------------------------------------------------------------

    private void swap(View content) {
        handler.removeCallbacksAndMessages(null);
        quietUntil = SystemClock.uptimeMillis() + 150;
        stage.removeAllViews();
        if (content.getParent() != null) ((ViewGroup) content.getParent()).removeView(content);
        stage.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        content.setAlpha(0f);
        content.animate().alpha(1f).setDuration(180).start();
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        unclipped(l);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setPadding(px(48), px(36), px(48), px(36));
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        unclipped(l);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER);
        return l;
    }

    /** Focus, the amber pulse and a pop grow a tile past its box: let it draw there instead of cutting it flat. */
    private static void unclipped(ViewGroup g) {
        g.setClipChildren(false);
        g.setClipToPadding(false);
    }

    private TextView text(CharSequence s, float sp, int color, int font) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(getResources().getFont(font), Typeface.NORMAL);
        return t;
    }

    private Tile tile(CharSequence s, float sp, float radiusDp) {
        Tile t = new Tile(this, radiusDp);
        t.setText(s);
        if (sp > 0) t.setTextSize(sp);
        return t;
    }

    private Tile button(String label, Runnable action) {
        Tile t = tile(label, 20, 28);
        t.setPadding(px(24), 0, px(24), 0);
        t.setOnClickListener(v -> action.run());
        return t;
    }

    private LinearLayout.LayoutParams buttonSize() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(56));
        return p;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams margins(LinearLayout.LayoutParams p, int l, int t, int r, int b) {
        p.setMargins(px(l), px(t), px(r), px(b));
        return p;
    }

    private int px(float dps) {
        return Math.round(dps * dp);
    }
}
