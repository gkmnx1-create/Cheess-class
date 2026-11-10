package com.chessclass.app.vision;

import android.graphics.Bitmap;
import android.util.Log;

import com.chessclass.app.vision.Side;

public class PieceClassifier {

    private static final String TAG = "OC/Classifier";

    private static final char[] START_WHITE_POV = {
        'r','n','b','q','k','b','n','r',
        'p','p','p','p','p','p','p','p',
        '.','.','.','.','.','.','.','.',
        '.','.','.','.','.','.','.','.',
        '.','.','.','.','.','.','.','.',
        '.','.','.','.','.','.','.','.',
        'P','P','P','P','P','P','P','P',
        'R','N','B','Q','K','B','N','R'
    };

    private SquareSampler.Sample[] templates;
    // Per-piece prototypes learned from the current chess-set artwork at bootstrap.
    // Index 0..5 = white P/N/B/R/Q/K, 6..11 = black p/n/b/r/q/k.
    // The second dimension separates light and dark board squares.
    private float[][][] typePrototypes = new float[12][2][SquareSampler.N * SquareSampler.N];
    private int[][] typePrototypeCounts = new int[12][2];
    private SquareSampler.Sample[] previousFrame;
    private char[] previousLabels;
    private char[] labels;
    private float emptyEdgeMax;
    private float occupiedEdgeMin;
    private float pieceColorThreshold;
    private float frameChangeThreshold;
    private float whitePieceLumAvg;
    private float blackPieceLumAvg;
    private boolean trained;
    private Side bootstrapSide;

    public boolean isTrained() { return trained; }
    public Side bootstrapSide() { return bootstrapSide; }

    public synchronized boolean tryBootstrap(Bitmap bmp, BoardDetector.Board board) {
        if (board == null) return false;
        SquareSampler.Sample[] samples = sampleAll(bmp, board);
        if (samples == null) return false;

        float maxEmptyEdge = 0;
        int emptyCount = 0;
        for (int r = 2; r <= 5; r++) for (int f = 0; f < 8; f++) {
            float e = samples[r * 8 + f].edgeEnergy;
            if (e > maxEmptyEdge) maxEmptyEdge = e;
            emptyCount++;
        }

        float minOccupiedEdge = Float.MAX_VALUE;
        int occCount = 0;
        for (int r = 0; r < 8; r++) {
            if (r >= 2 && r <= 5) continue;
            for (int f = 0; f < 8; f++) {
                float e = samples[r * 8 + f].edgeEnergy;
                if (e < minOccupiedEdge) minOccupiedEdge = e;
                occCount++;
            }
        }

        if (minOccupiedEdge < maxEmptyEdge * 1.5f) {
            Log.i(TAG, "bootstrap reject: minOccupied=" + minOccupiedEdge
                    + " maxEmpty=" + maxEmptyEdge);
            return false;
        }

        emptyEdgeMax = maxEmptyEdge;
        occupiedEdgeMin = minOccupiedEdge;
        pieceColorThreshold = (maxEmptyEdge + minOccupiedEdge) * 0.5f;

        float topLumSum = 0, botLumSum = 0;
        int topN = 0, botN = 0;
        for (int f = 0; f < 8; f++) {
            for (int r : new int[]{0, 1}) {
                topLumSum += samples[r * 8 + f].meanLum;
                topN++;
            }
            for (int r : new int[]{6, 7}) {
                botLumSum += samples[r * 8 + f].meanLum;
                botN++;
            }
        }
        if (topN == 0 || botN == 0) return false;
        float topAvg = topLumSum / topN;
        float botAvg = botLumSum / botN;
        Side side = botAvg > topAvg ? Side.WHITE : Side.BLACK;

        char[] map = side == Side.WHITE ? START_WHITE_POV : flipLabels(START_WHITE_POV);
        templates = samples;
        labels = map;
        bootstrapSide = side;
        buildTypePrototypes(samples, map);

        float whiteSum = 0; int whiteN = 0;
        float blackSum = 0; int blackN = 0;
        for (int i = 0; i < 64; i++) {
            char c = labels[i];
            if (c == '.') continue;
            float lum = samples[i].meanLum;
            if (Character.isUpperCase(c)) { whiteSum += lum; whiteN++; }
            else                          { blackSum += lum; blackN++; }
        }
        whitePieceLumAvg = whiteN > 0 ? whiteSum / whiteN : 0.7f;
        blackPieceLumAvg = blackN > 0 ? blackSum / blackN : 0.3f;

        previousFrame = samples;
        previousLabels = new char[64];
        for (int i = 0; i < 64; i++) previousLabels[i] = labels[i];

        float maxBootstrapNoise = 0;
        for (int i = 0; i < 64; i++) {
            if (labels[i] != '.') continue;
            for (int j = i + 1; j < 64; j++) {
                if (labels[j] != '.') continue;
                if ((((i/8)+(i%8))&1) != (((j/8)+(j%8))&1)) continue;
                float d = SquareSampler.lumDistance(samples[i].feature, samples[j].feature);
                if (d > maxBootstrapNoise) maxBootstrapNoise = d;
            }
        }
        // lumDistance is a SUM over 400 pixels. Convert it to mean-square
        // distance before setting the frame-change threshold; otherwise the
        // threshold can become so large that moved pieces are never detected.
        float meanBootstrapNoise = maxBootstrapNoise /
                (SquareSampler.N * SquareSampler.N);
        frameChangeThreshold = Math.max(0.006f,
                Math.min(0.08f, meanBootstrapNoise * 2.0f + 0.003f));

        trained = true;
        Log.i(TAG, "BOOTSTRAPPED side=" + side
                + " emptyMax=" + maxEmptyEdge
                + " occMin=" + minOccupiedEdge
                + " thr=" + pieceColorThreshold
                + " frameChangeThr=" + frameChangeThreshold);
        return true;
    }

    public synchronized char[][] classifyBoard(Bitmap bmp, BoardDetector.Board board) {
        if (!trained) return null;
        SquareSampler.Sample[] samples = sampleAll(bmp, board);
        if (samples == null) return null;

        char[][] grid = new char[8][8];
        for (int r = 0; r < 8; r++) {
            for (int f = 0; f < 8; f++) {
                int idx = r * 8 + f;
                if (previousFrame != null && previousLabels != null) {
                    float fd = SquareSampler.lumDistance(
                            samples[idx].feature, previousFrame[idx].feature) /
                            (SquareSampler.N * SquareSampler.N);
                    if (fd < frameChangeThreshold) {
                        grid[r][f] = previousLabels[idx];
                        continue;
                    }
                }
                grid[r][f] = classifyOne(samples[idx], r, f);
            }
        }
        return grid;
    }

    public synchronized void commitConfirmed(char[][] grid, Bitmap bmp, BoardDetector.Board board) {
        if (!trained || grid == null) return;
        SquareSampler.Sample[] samples = sampleAll(bmp, board);
        if (samples == null) return;
        previousFrame = samples;
        if (previousLabels == null) previousLabels = new char[64];
        for (int r = 0; r < 8; r++) {
            for (int f = 0; f < 8; f++) {
                previousLabels[r * 8 + f] = grid[r][f] == 0 ? '.' : grid[r][f];
            }
        }
    }

    private SquareSampler.Sample[] sampleAll(Bitmap bmp, BoardDetector.Board board) {
        if (board.square < 8) return null;
        int rad = Math.max(4, (int) (board.square * 0.32f));
        SquareSampler.Sample[] samples = new SquareSampler.Sample[64];
        for (int r = 0; r < 8; r++) {
            for (int f = 0; f < 8; f++) {
                int cx = Math.round(board.left + (f + 0.5f) * board.square);
                int cy = Math.round(board.top  + (r + 0.5f) * board.square);
                samples[r * 8 + f] = SquareSampler.sample(bmp, cx, cy, rad);
            }
        }
        return samples;
    }

    private char[] flipLabels(char[] src) {
        char[] o = new char[64];
        for (int r = 0; r < 8; r++)
            for (int f = 0; f < 8; f++)
                o[r * 8 + f] = src[(7 - r) * 8 + (7 - f)];
        return o;
    }

    private int typeIndex(char piece) {
        char p = Character.toUpperCase(piece);
        int base;
        switch (p) {
            case 'P': base = 0; break;
            case 'N': base = 1; break;
            case 'B': base = 2; break;
            case 'R': base = 3; break;
            case 'Q': base = 4; break;
            case 'K': base = 5; break;
            default: return -1;
        }
        return Character.isUpperCase(piece) ? base : base + 6;
    }

    private void buildTypePrototypes(SquareSampler.Sample[] samples, char[] pieceLabels) {
        typePrototypes = new float[12][2][SquareSampler.N * SquareSampler.N];
        typePrototypeCounts = new int[12][2];
        for (int i = 0; i < 64; i++) {
            char piece = pieceLabels[i];
            if (piece == '.') continue;
            int type = typeIndex(piece);
            if (type < 0) continue;
            int row = i / 8;
            int file = i % 8;
            int squareTone = ((row + file) & 1) == 0 ? 0 : 1;
            float[] source = samples[i].feature;
            for (int j = 0; j < source.length; j++) {
                typePrototypes[type][squareTone][j] += source[j];
            }
            typePrototypeCounts[type][squareTone]++;
        }
        for (int type = 0; type < 12; type++) {
            for (int tone = 0; tone < 2; tone++) {
                int count = typePrototypeCounts[type][tone];
                if (count == 0) continue;
                for (int j = 0; j < typePrototypes[type][tone].length; j++) {
                    typePrototypes[type][tone][j] /= count;
                }
            }
        }
    }

    private char classifyOne(SquareSampler.Sample sample, int row, int file) {
        if (sample.edgeEnergy < pieceColorThreshold) return '.';

        float distWhite = Math.abs(sample.meanLum - whitePieceLumAvg);
        float distBlack = Math.abs(sample.meanLum - blackPieceLumAvg);
        boolean isWhitePiece = distWhite < distBlack;
        int squareTone = ((row + file) & 1) == 0 ? 0 : 1;
        int firstType = isWhitePiece ? 0 : 6;
        float best = Float.MAX_VALUE;
        char bestPiece = isWhitePiece ? 'P' : 'p';

        // Compare against the average shape of each piece type, rather than
        // one individual starting-square sample. This reduces noise from
        // shadows, animation frames and small artwork differences.
        for (int offset = 0; offset < 6; offset++) {
            int type = firstType + offset;
            if (typePrototypeCounts[type][squareTone] == 0) continue;
            float distance = SquareSampler.lumDistance(
                    sample.feature, typePrototypes[type][squareTone]);
            if (distance < best) {
                best = distance;
                bestPiece = isWhitePiece
                        ? new char[]{'P', 'N', 'B', 'R', 'Q', 'K'}[offset]
                        : new char[]{'p', 'n', 'b', 'r', 'q', 'k'}[offset];
            }
        }
        return bestPiece;
    }

    public synchronized void reset() {
        templates = null;
        labels = null;
        typePrototypes = new float[12][2][SquareSampler.N * SquareSampler.N];
        typePrototypeCounts = new int[12][2];
        previousFrame = null;
        previousLabels = null;
        trained = false;
        bootstrapSide = null;
    }
}
