package com.gathin.tvhome;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

final class WallpaperRotation {
    /** Playback orders persisted in config.json (wallpaper_order). */
    static final String ORDER_SEQUENTIAL = "sequential";
    static final String ORDER_SHUFFLE = "shuffle";

    static boolean validOrder(String order) {
        return ORDER_SEQUENTIAL.equals(order) || ORDER_SHUFFLE.equals(order);
    }

    static List<File> images(File directory) {
        List<File> result = new ArrayList<>();
        File[] entries = directory == null ? null : directory.listFiles();
        if (entries == null) return result;
        Arrays.sort(entries, Comparator.comparing(File::getName));
        for (File entry : entries) {
            String name = entry.getName().toLowerCase(Locale.ROOT);
            if (entry.isFile() && entry.length() > 0 && entry.length() <= 10 * 1024 * 1024
                    && (name.endsWith(".jpg") || name.endsWith(".jpeg")
                    || name.endsWith(".png") || name.endsWith(".webp"))) {
                result.add(entry);
                if (result.size() == 100) break;
            }
        }
        return result;
    }

    static File find(List<File> images, String name) {
        for (File image : images) if (image.getName().equals(name)) return image;
        return null;
    }

    /** The on-TV choice (Launcher settings, Wallpaper) that turns the wallpaper off. */
    static final String CHOICE_NONE = "none";

    /**
     * What an on-TV wallpaper choice puts on Home in place of the theme's rotation: null when the theme decides
     * (no choice, or a picture that is no longer there), an empty list for {@link #CHOICE_NONE}, or the one picture
     * chosen from the theme's collection or its single {@code single} wallpaper.
     */
    static List<File> chosen(List<File> collection, File single, String choice) {
        if (choice == null) return null;
        if (CHOICE_NONE.equals(choice)) return Collections.emptyList();
        File picked = find(collection, choice);
        if (picked == null && single != null && choice.equals(single.getName())) picked = single;
        return picked == null ? null : Collections.singletonList(picked);
    }

    static File pick(List<File> images, String previous, Random random) {
        List<File> candidates = new ArrayList<>(images);
        if (candidates.size() > 1) candidates.removeIf(image -> image.getName().equals(previous));
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    /**
     * The next picture in the sorted collection, wrapping to the first at the end. The list from
     * {@link #images(File)} is sorted by filename, so this is a stable order across restarts. A
     * previous name that is no longer in the collection starts again at the first picture.
     */
    static File next(List<File> images, String previous) {
        if (images.isEmpty()) return null;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(previous)) {
                return images.get((i + 1) % images.size());
            }
        }
        return images.get(0);
    }

    /** Picks the next picture for {@code order}; an unknown order keeps the historical shuffle. */
    static File pick(List<File> images, String previous, String order, Random random) {
        return ORDER_SEQUENTIAL.equals(order) ? next(images, previous) : pick(images, previous, random);
    }

    /** wallpaper_dim's range, and the step the wallpaper preview changes it by. */
    static final int MAX_DIM = 90;
    static final int DIM_STEP = 10;

    /**
     * The wallpaper preview's Left and Right: the picture {@code delta} places from {@code current} in the sorted
     * collection, wrapping at both ends. A current name that is not in the collection starts from the first.
     */
    static File step(List<File> images, String current, int delta) {
        if (images.isEmpty()) return null;
        int at = -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(current)) at = i;
        }
        if (at < 0) return images.get(0);
        return images.get(Math.floorMod(at + delta, images.size()));
    }

    /**
     * The wallpaper preview's Up and Down: the next multiple of DIM_STEP above or below {@code dim}, within 0 to
     * MAX_DIM. A theme's 35 goes to 40 or 30, so every press lands on the same steps.
     */
    static int stepDim(int dim, int delta) {
        int next = delta > 0 ? (dim / DIM_STEP + 1) * DIM_STEP : ((dim + DIM_STEP - 1) / DIM_STEP - 1) * DIM_STEP;
        return Math.max(0, Math.min(MAX_DIM, next));
    }

    static boolean due(long previous, long now, long interval) {
        return interval > 0 && (previous == 0 || now < previous || now - previous >= interval);
    }
}
