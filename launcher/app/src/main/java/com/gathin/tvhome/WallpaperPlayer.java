package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Displays and rotates a theme's local wallpaper images while Home is visible. In preview mode (Home's Settings
 * menu) the rotation pauses and the owner steps through the theme's pictures and dim levels instead.
 */
final class WallpaperPlayer {
    private final Context context;
    private final FrameLayout root;
    private final ImageView image;
    private final int backgroundColor;
    private final List<File> files;
    private final long interval;
    private final String order;
    private final SharedPreferences preferences;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private final int width;
    /** config wallpaper_true_color: ARGB_8888 instead of RGB_565. */
    private final Bitmap.Config bitmapConfig;
    private final int height;
    /** wallpaper_dim as an ARGB black, painted into each picture once instead of a full-screen shade view. */
    private int dimColor;
    /** wallpaper_dim in percent; the preview changes it. */
    private int dim;
    private boolean previewing;
    /**
     * The preview's dim: a shade view over undimmed pictures while it runs, so Up and Down act at once. It goes
     * when the preview ends, and the picture is decoded again with the dim painted in.
     */
    private View shade;
    /** Every picture of the theme, which the preview steps through even while one picture is chosen on the TV. */
    private List<File> previewImages = new ArrayList<>();
    /** The picture the preview last asked for; a load still in flight moves on to it when it lands. */
    private File previewTarget;
    /** The picture on screen, released once the next one has been drawn. */
    private Bitmap current;
    private boolean active;
    private boolean closed;
    private boolean loading;
    private String displayed = "";
    /** wallpaper_captions: the shown picture's name from wallpapers/captions.txt; null when off or there are none. */
    private final TextView caption;
    private final Map<String, String> names;
    /** wallpaper_moods: the collection's groups by time of day; empty rotates through all of it. */
    private final Map<String, Set<String>> moods;
    /** config.xml latitude and longitude for the sun's times, and today's {sunrise, sunset} once worked out. */
    private final String latitude, longitude;
    private LocalDate sunDay;
    private int[] sun;

    WallpaperPlayer(Context context, FrameLayout root, Config config) {
        this.context = context;
        this.root = root;
        backgroundColor = config.backgroundColor;
        files = new ArrayList<>(config.wallpapers);
        interval = config.wallpaperRotationMinutes * 60_000L;
        bitmapConfig = config.wallpaperTrueColor ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
        // The dim is painted into the picture (decode), so no full-screen shade layer is drawn on every frame.
        dim = config.wallpaperDim;
        dimColor = dimColor(dim);
        order = config.wallpaperOrder;
        preferences = context.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE);
        moods = config.wallpaperMoods;
        latitude = moods.isEmpty() ? "" : context.getString(R.string.latitude);
        longitude = moods.isEmpty() ? "" : context.getString(R.string.longitude);
        // Playback state belongs to one collection. Changing the collection starts from its first
        // picture instead of resuming a filename that belonged to the previous one.
        String collection = collectionKey(files);
        if (!collection.equals(preferences.getString("collection", ""))) {
            preferences.edit().putString("collection", collection)
                    .remove("image").remove("changed").apply();
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        names = config.wallpaperCaptions && config.wallpaperEnabled && !files.isEmpty()
                ? WallpaperCaptions.read(files.get(0).getParentFile()) : Collections.emptyMap();
        caption = names.isEmpty() ? null : makeCaption(context, root);
        if (config.wallpaperEnabled && !files.isEmpty()) {
            root.addView(image, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            showFirst();
        }
    }

    /**
     * The picture Home opens with is decoded right here, on the main thread, as the single wallpaper always was:
     * Home's first frame already has it instead of drawing the plain colour and the picture popping in later.
     * Only the later changes of a rotation decode in the background.
     */
    private void showFirst() {
        String previous = preferences.getString("image", "");
        List<File> playable = playable();
        File first = WallpaperRotation.find(playable, previous);
        if (first == null) first = WallpaperRotation.pick(playable, previous, order, random);
        while (first != null) {
            Bitmap bitmap = decode(first);
            if (bitmap != null) {
                show(bitmap);
                // The picture covers the whole screen: the root's plain colour under it is overdraw.
                root.setBackground(null);
                displayed = first.getName();
                showCaption(false);
                if (!displayed.equals(previous)) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
                return;
            }
            files.remove(first);   // unreadable or over 4K: try another, or keep the plain colour
            playable = playable();
            first = WallpaperRotation.pick(playable, previous, order, random);
        }
    }

    void start() {
        active = true;
        update();
    }

    private static int dimColor(int percent) {
        return percent > 0 ? (percent * 255 / 100) << 24 : 0;
    }

    /** Whether a picture is on screen: only then is there anything to preview. */
    boolean showing() {
        return image.getParent() != null && !displayed.isEmpty();
    }

    int dim() {
        return dim;
    }

    /** The preview's Up and Down (and Back, which restores the dim it started with): the shade changes at once. */
    void setDim(int percent) {
        dim = percent;
        if (shade != null) shade.setBackgroundColor(dimColor(percent));
    }

    /**
     * Pauses the rotation; the preview steps through {@code images}, the theme's whole collection. The picture on
     * screen is decoded again without its dim, which the shade then draws.
     */
    void beginPreview(List<File> images) {
        previewing = true;
        previewImages = images.isEmpty() ? new ArrayList<>(files) : new ArrayList<>(images);
        dimColor = 0;
        shade = new View(context);
        shade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        shade.setBackgroundColor(dimColor(dim));
        root.addView(shade, root.indexOfChild(image) + 1, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        File shown = WallpaperRotation.find(previewImages, displayed);
        if (shown == null) shown = WallpaperRotation.find(files, displayed);
        if (shown != null) {
            previewTarget = shown;
            if (!loading) load(shown);
        }
    }

    /** Shows the picture {@code delta} places from the one asked for last, and returns its name. */
    String step(int delta) {
        File next = WallpaperRotation.step(previewImages,
                previewTarget != null ? previewTarget.getName() : displayed, delta);
        if (next == null) return displayed;
        boolean changed = previewTarget != next;
        previewTarget = next;
        if (!loading && changed) load(next);
        return next.getName();
    }

    /**
     * Back to the rotation, which carries on from the picture on screen, now with the dim painted in again. A
     * picture outside the rotation (another one is chosen on the TV) gives way to the rotation's own.
     */
    void endPreview() {
        previewing = false;
        previewTarget = null;
        dimColor = dimColor(dim);
        if (shade != null) root.removeView(shade);
        shade = null;
        File shown = WallpaperRotation.find(files, displayed);
        if (shown != null) {
            preferences.edit().putString("image", displayed).putLong("changed", System.currentTimeMillis()).apply();
            if (!loading) load(shown);
        } else {
            update();
        }
    }

    /** Decodes one picture and shows it; in the preview, a newer pick waiting meanwhile goes next. */
    private void load(File next) {
        loading = true;
        int decodedDim = dimColor;   // decode() paints the dim it finds; the preview may end before this lands
        decoder.execute(() -> {
            Bitmap bitmap = decode(next);
            root.post(() -> {
                loading = false;
                if (closed) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                if (bitmap != null) {
                    show(bitmap);
                    displayed = next.getName();
                    showCaption(true);
                }
                File again = WallpaperRotation.find(files, displayed);
                if (previewing && previewTarget != null && previewTarget != next) load(previewTarget);
                else if (!previewing && decodedDim != dimColor && again != null) load(again);   // undimmed: paint it in
                else if (!previewing) update();
            });
        });
    }

    void stop() {
        active = false;
    }

    /** Drops the wallpaper texture when Home is hidden and Android asks the process to free memory. */
    void releaseForMemoryPressure() {
        if (current == null) return;
        image.setImageDrawable(null);
        // The renderer may still have a queued frame that references this hardware bitmap. Drop our references and
        // let Android reclaim its native buffer after rendering releases it instead of recycling it mid-frame.
        current = null;
        displayed = "";
        root.setBackgroundColor(backgroundColor);
    }

    /** The pictures the rotation picks from now: wallpaper_moods' group for the time of day, or the whole collection. */
    private List<File> playable() {
        if (moods.isEmpty()) return files;
        LocalDateTime now = LocalDateTime.now();
        if (!now.toLocalDate().equals(sunDay)) {
            sunDay = now.toLocalDate();
            sun = WallpaperMood.sun(sunDay, latitude, longitude, ZoneId.systemDefault());
        }
        return WallpaperMood.select(files, moods, WallpaperMood.timeOfDay(now.getHour() * 60 + now.getMinute(), sun[0], sun[1]));
    }

    void close() {
        active = false;
        closed = true;
        decoder.shutdownNow();
    }

    void update() {
        if (!active || closed || loading || previewing || files.isEmpty()) return;
        String previous = preferences.getString("image", "");
        // A picture outside the time of day's group (the group changed) is replaced at once, not after the interval.
        List<File> playable = playable();
        File selected = WallpaperRotation.find(playable, previous);
        long changed = preferences.getLong("changed", 0);
        if (selected == null || WallpaperRotation.due(changed, System.currentTimeMillis(), interval)) {
            selected = WallpaperRotation.pick(playable, previous, order, random);
        }
        if (selected == null || selected.getName().equals(displayed)) return;
        File next = selected;
        loading = true;
        decoder.execute(() -> {
            Bitmap bitmap = decode(next);
            root.post(() -> {
                loading = false;
                if (closed || !active || previewing) {
                    if (bitmap != null) bitmap.recycle();
                    // the preview began while this picture was loading: its own pick goes first
                    if (previewing && !closed && previewTarget != null) load(previewTarget);
                    return;
                }
                if (bitmap == null) {
                    files.remove(next);
                    Log.w("TvHome.Wallpaper", "Skipping unreadable image: " + next.getName());
                    update();
                    return;
                }
                show(bitmap);
                displayed = next.getName();
                showCaption(true);
                if (!displayed.equals(previous) || changed == 0) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
            });
        });
    }

    /**
     * Small light text under the top-right buttons, above everything else and never focusable. It sits where the
     * top bar ends in every layout, so it does not cover the clock, the stats line or the tiles.
     */
    private static TextView makeCaption(Context context, FrameLayout root) {
        float dp = context.getResources().getDisplayMetrics().density;
        TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        text.setTextColor(0xD9FFFFFF);
        text.setShadowLayer(6 * dp, 0, dp, 0xCC000000);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setMaxWidth(Math.round(520 * dp));
        text.setFocusable(false);
        text.setAlpha(0f);
        try {
            text.setTypeface(UiFont.get(context, R.string.font_regular));
        } catch (Exception ignored) {
            // the system font is fine
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        params.topMargin = Math.round(110 * dp);
        params.setMarginEnd(Math.round(62 * dp));
        root.addView(text, params);
        return text;
    }

    /** The displayed picture's name, faded across a change; a picture without a name hides the caption. */
    private void showCaption(boolean fade) {
        if (caption == null) return;
        String name = names.get(displayed);
        caption.animate().cancel();
        if (name == null) {
            caption.setAlpha(0f);
            return;
        }
        caption.setContentDescription(name);
        if (!fade || caption.getAlpha() == 0f) {
            caption.setText(name);
            caption.setAlpha(1f);
            return;
        }
        caption.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            caption.setText(name);
            caption.animate().alpha(1f).setDuration(350).start();
        }).start();
    }

    /** Names plus sizes: enough to notice a different collection without hashing every image. */
    private static String collectionKey(List<File> images) {
        StringBuilder key = new StringBuilder();
        for (File file : images) key.append(file.getName()).append(':').append(file.length()).append(';');
        return key.toString();
    }

    /** Puts a picture on screen and releases the previous one after a frame has drawn the new one. */
    private void show(Bitmap bitmap) {
        Bitmap old = current;
        current = bitmap;
        // The wallpaper covers the whole root; avoid drawing the fallback colour under it.
        root.setBackground(null);
        image.setImageBitmap(bitmap);
        if (old != null && old != bitmap) image.postDelayed(old::recycle, 500);
    }

    private Bitmap decode(File file) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int width = options.outWidth, height = options.outHeight;
            if (width <= 0 || height <= 0) return null;
            int sample = WallpaperSize.sampleSize(width, height, this.width, this.height);
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(width / sample, height / sample, this.width, this.height);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = bitmapConfig;
            options.inMutable = dimColor != 0;
            Bitmap decoded = BitmapFactory.decodeFile(file.getPath(), options);
            if (decoded == null) return null;
            if (dimColor != 0) new Canvas(decoded).drawColor(dimColor);
            // One copy in GPU memory instead of a software bitmap plus its texture; the decode stays off the UI
            // thread for rotations, and Home's return no longer uploads 4-8 MB to the GPU.
            Bitmap gpu = decoded.copy(Bitmap.Config.HARDWARE, false);
            if (gpu == null) return decoded;
            decoded.recycle();
            return gpu;
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }
}
