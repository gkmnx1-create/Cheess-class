package com.chessclass.app.vision;

import android.graphics.Bitmap;
import android.util.Log;

public class BoardDetector {

    public static class Board {
        public final int left, top, side;
        public final float square;
        public final double confidence;
        public Board(int left, int top, int side, double confidence) {
            this.left = left; this.top = top; this.side = side;
            this.square = side / 8f;
            this.confidence = confidence;
        }
    }

    private static final String TAG = "OC/Detector";
    // Keep the full 720px capture edge for more precise automatic board localization.
    // Board dimensions are detected from the image; this is not a fixed board size.
    private static final int WORK_MAX_EDGE = 720;
    private static final double ACCEPT_THRESHOLD = 10.0;

    public static Board locate(Bitmap src) {
        if (src == null) return null;
        long t0 = System.nanoTime();

        int W0 = src.getWidth(), H0 = src.getHeight();
        float scale = Math.min(1.0f, (float) WORK_MAX_EDGE / Math.max(W0, H0));
        int W = Math.max(64, Math.round(W0 * scale));
        int H = Math.max(64, Math.round(H0 * scale));

        Bitmap work = (scale < 0.999f)
                ? Bitmap.createScaledBitmap(src, W, H, true)
                : src;

        int[] argb = new int[W * H];
        work.getPixels(argb, 0, W, 0, 0, W, H);
        if (work != src) work.recycle();

        int[] gray = new int[W * H];
        int[] chroma = new int[W * H];
        for (int i = 0; i < gray.length; i++) {
            int p = argb[i];
            int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
            gray[i] = (r * 299 + g * 587 + b * 114) / 1000;
            // Some board themes use similar brightness but different square hues.
            chroma[i] = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
        }

        int Wp = W + 1;
        long[] ii = buildIntegral(gray, W, H, Wp);
        long[] ci = buildIntegral(chroma, W, H, Wp);

        int minSide = Math.max(80, Math.min(W, H) / 3);
        // Allow boards that touch the screen edges; do not require an artificial 2% margin.
        int maxSide = Math.min(W, H);

        Candidate best = null;

        int sizeStep = Math.max(2, (maxSide - minSide) / 48);
        for (int s = minSide; s <= maxSide; s += sizeStep) {
            int cell = s / 8;
            if (cell < 5) continue;
            int corrected = cell * 8;
            int posStep = Math.max(2, cell / 3);
            for (int y = 0; y + corrected <= H; y += posStep) {
                for (int x = 0; x + corrected <= W; x += posStep) {
                    double score = checkerScoreCombined(ii, ci, Wp, x, y, cell);
                    if (best == null || score > best.score) {
                        best = new Candidate(x, y, corrected, cell, score);
                    }
                }
            }
        }

        if (best == null) { Log.w(TAG, "locate: no candidate in " + W + "x" + H); return null; }

        Candidate refined = refine(ii, ci, Wp, W, H, best);

        long elapsed = (System.nanoTime() - t0) / 1_000_000;
        Log.d(TAG, "locate: " + W + "x" + H + " best=" + refined.score
                + " bbox=(" + refined.x + "," + refined.y + ",s=" + refined.side + ")"
                + " in " + elapsed + " ms");

        if (refined.score < ACCEPT_THRESHOLD) { Log.w(TAG, "locate rejected: score=" + refined.score + " threshold=" + ACCEPT_THRESHOLD); return null; }

        int left = Math.round(refined.x / scale);
        int top  = Math.round(refined.y / scale);
        int side = Math.round(refined.side / scale);
        return new Board(left, top, side, refined.score);
    }

    private static Candidate refine(long[] ii, long[] ci, int Wp, int W, int H, Candidate c) {
        Candidate best = c;
        int cell = c.cell;
        for (int dy = -cell; dy <= cell; dy += 2) {
            for (int dx = -cell; dx <= cell; dx += 2) {
                int x = c.x + dx, y = c.y + dy;
                if (x < 0 || y < 0 || x + c.side > W || y + c.side > H) continue;
                double sc = checkerScoreCombined(ii, ci, Wp, x, y, cell);
                if (sc > best.score) best = new Candidate(x, y, c.side, cell, sc);
            }
        }
        for (int ds = -cell / 4; ds <= cell / 4; ds += 2) {
            int newCell = cell + ds / 8;
            if (newCell < 5) continue;
            int newSide = newCell * 8;
            int x = best.x, y = best.y;
            if (x + newSide > W || y + newSide > H) continue;
            double sc = checkerScoreCombined(ii, ci, Wp, x, y, newCell);
            if (sc > best.score) best = new Candidate(x, y, newSide, newCell, sc);
        }
        return best;
    }

    private static long[] buildIntegral(int[] values, int W, int H, int Wp) {
        long[] integral = new long[Wp * (H + 1)];
        for (int y = 0; y < H; y++) {
            long row = 0;
            int srcBase = y * W;
            int dstBase = (y + 1) * Wp;
            int prevBase = y * Wp;
            for (int x = 0; x < W; x++) {
                row += values[srcBase + x];
                integral[dstBase + x + 1] = integral[prevBase + x + 1] + row;
            }
        }
        return integral;
    }

    // Brightness OR chroma contrast can identify the alternating square pattern.
    private static double checkerScoreCombined(long[] gray, long[] chroma,
                                               int Wp, int x, int y, int cell) {
        return Math.max(checkerScore(gray, Wp, x, y, cell),
                        checkerScore(chroma, Wp, x, y, cell));
    }

    private static double checkerScore(long[] ii, int Wp, int x0, int y0, int cell) {
        int samp = Math.max(2, cell / 5);
        int inset = Math.max(2, cell / 6);
        double sumLight = 0, sumDark = 0;
        double squareLight = 0, squareDark = 0;
        int countLight = 0, countDark = 0;

        // Keep this hot path allocation-free: board search evaluates many candidates
        // per frame, so temporary arrays here create avoidable garbage-collection pauses.
        for (int r = 0; r < 8; r++) {
            for (int f = 0; f < 8; f++) {
                int cx = x0 + f * cell;
                int cy = y0 + r * cell;
                double a = boxMean(ii, Wp, cx + inset, cy + inset, samp);
                double b = boxMean(ii, Wp, cx + cell - inset - samp, cy + inset, samp);
                double c = boxMean(ii, Wp, cx + inset, cy + cell - inset - samp, samp);
                double d = boxMean(ii, Wp, cx + cell - inset - samp,
                        cy + cell - inset - samp, samp);
                double median = median4(a, b, c, d);
                if (((r + f) & 1) == 0) {
                    sumLight += median;
                    squareLight += median * median;
                    countLight++;
                } else {
                    sumDark += median;
                    squareDark += median * median;
                    countDark++;
                }
            }
        }

        double meanL = sumLight / countLight;
        double meanD = sumDark / countDark;
        double varianceL = Math.max(0, squareLight / countLight - meanL * meanL);
        double varianceD = Math.max(0, squareDark / countDark - meanD * meanD);
        double sep = Math.abs(meanL - meanD);
        return sep * sep / (4.0 + Math.sqrt(varianceL) + Math.sqrt(varianceD));
    }

    private static double median4(double a, double b, double c, double d) {
        double t;
        if (a > b) { t = a; a = b; b = t; }
        if (c > d) { t = c; c = d; d = t; }
        if (a > c) { t = a; a = c; c = t; }
        if (b > d) { t = b; b = d; d = t; }
        if (b > c) { t = b; b = c; c = t; }
        return (b + c) * 0.5;
    }

    private static double boxMean(long[] ii, int Wp, int x, int y, int s) {
        int x2 = x + s, y2 = y + s;
        long sum = ii[y2 * Wp + x2] - ii[y * Wp + x2] - ii[y2 * Wp + x] + ii[y * Wp + x];
        return (double) sum / (s * s);
    }

    private static final class Candidate {
        final int x, y, side, cell;
        final double score;
        Candidate(int x, int y, int side, int cell, double score) {
            this.x = x; this.y = y; this.side = side; this.cell = cell; this.score = score;
        }
    }
}
