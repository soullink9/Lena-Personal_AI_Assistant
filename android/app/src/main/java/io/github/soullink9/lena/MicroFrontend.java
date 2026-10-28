package io.github.soullink9.lena;

/**
 * The audio front end microWakeWord models are trained on: TensorFlow Lite Micro's
 * "audio_microfrontend" (window, fixed-point FFT, mel filterbank, noise reduction, PCAN gain
 * control, log scale), with the settings Home Assistant's voice satellites use. Ported line for
 * line from the C (Apache License 2.0, The TensorFlow Authors; the FFT is KISS FFT, BSD,
 * Mark Borgerding) so it produces the same numbers bit for bit; tools/frontend_check compares
 * the two.
 *
 * Feed it 16 kHz mono 16-bit audio with {@link #process}; every 10 ms of audio it produces 40
 * features (unsigned 16-bit values) in {@link #features}.
 *
 * Written in Java because it's all fixed-point integer arithmetic with deliberate C overflow
 * behaviour, which Java's int, long and short types reproduce exactly.
 */
public final class MicroFrontend {

    public static final int CHANNELS = 40;
    /** One step of features: 10 ms at 16 kHz. */
    public static final int STEP = 160;

    private static final int SIZE = 480;          // 30 ms window
    private static final int FFT = 512;            // next power of two
    private static final int HALF = FFT / 2;       // complex FFT size inside the real FFT
    private static final int WINDOW_BITS = 12;
    private static final int FILTERBANK_BITS = 12;
    private static final int NR_BITS = 14;
    private static final int SNR_BITS = 12;
    private static final int OUTPUT_BITS = 6;
    private static final int SMOOTHING_BITS = 10;
    private static final int SCALE_SHIFT = 6;
    private static final long U32 = 0xFFFFFFFFL;

    private static final int[] LOG_LUT = {
        0, 224, 442, 654, 861, 1063, 1259, 1450, 1636, 1817, 1992, 2163,
        2329, 2490, 2646, 2797, 2944, 3087, 3224, 3358, 3487, 3611, 3732, 3848,
        3960, 4068, 4172, 4272, 4368, 4460, 4549, 4633, 4714, 4791, 4864, 4934,
        5001, 5063, 5123, 5178, 5231, 5280, 5326, 5368, 5408, 5444, 5477, 5507,
        5533, 5557, 5578, 5595, 5610, 5622, 5631, 5637, 5640, 5641, 5638, 5633,
        5626, 5615, 5602, 5586, 5568, 5547, 5524, 5498, 5470, 5439, 5406, 5370,
        5332, 5291, 5249, 5203, 5156, 5106, 5054, 5000, 4944, 4885, 4825, 4762,
        4697, 4630, 4561, 4490, 4416, 4341, 4264, 4184, 4103, 4020, 3935, 3848,
        3759, 3668, 3575, 3481, 3384, 3286, 3186, 3084, 2981, 2875, 2768, 2659,
        2549, 2437, 2323, 2207, 2090, 1971, 1851, 1729, 1605, 1480, 1353, 1224,
        1094, 963, 830, 695, 559, 421, 282, 142, 0, 0,
    };

    // ---- State ---------------------------------------------------------------------------

    private final short[] coefficients = new short[SIZE];
    private final short[] input = new short[SIZE];
    private final short[] windowed = new short[SIZE];
    private int inputUsed;
    private short maxAbs;

    private final short[] fftIn = new short[FFT];
    private final short[] fftR = new short[HALF + 1];
    private final short[] fftI = new short[HALF + 1];
    private final short[] tmpR = new short[HALF];
    private final short[] tmpI = new short[HALF];
    private final short[] twR = new short[HALF];
    private final short[] twI = new short[HALF];
    private final short[] superR = new short[HALF / 2];
    private final short[] superI = new short[HALF / 2];
    private final int[] factors = new int[16];

    private int startIndex;
    private int endIndex;
    private final short[] chanFreqStarts = new short[CHANNELS + 1];
    private final short[] chanWeightStarts = new short[CHANNELS + 1];
    private final short[] chanWidths = new short[CHANNELS + 1];
    private short[] weights;
    private short[] unweights;
    private final long[] work = new long[CHANNELS + 1];
    private final int[] energy = new int[HALF + 1 + 8]; // padded channels read a little past the end

    private final int evenSmoothing;
    private final int oddSmoothing;
    private final int minSignalRemaining;
    private final int[] estimate = new int[CHANNELS];

    private final short[] gainLut = new short[4 * 32 - 3];
    private final int snrShift;

    private final int[] signal = new int[CHANNELS];

    /** The latest 40 features, valid when {@link #process} reported a frame. */
    public final int[] features = new int[CHANNELS];

    public MicroFrontend() {
        // Window: Hann, in Q12.
        float arg = (float) Math.PI * 2.0f / ((float) SIZE);
        for (int i = 0; i < SIZE; i++) {
            float v = 0.5f - (0.5f * cosf(arg * (i + 0.5f)));
            coefficients[i] = (short) floorf(v * (1 << WINDOW_BITS) + 0.5f);
        }

        // FFT twiddles (KISS FFT, 16-bit fixed point).
        for (int i = 0; i < HALF; i++) {
            double phase = -2 * 3.141592653589793238462643383279502884197169399375105820974944 * i / HALF;
            twR[i] = (short) Math.floor(.5 + 32767 * StrictMath.cos(phase));
            twI[i] = (short) Math.floor(.5 + 32767 * StrictMath.sin(phase));
        }
        for (int i = 0; i < HALF / 2; i++) {
            double phase = -3.14159265358979323846264338327 * ((double) (i + 1) / HALF + .5);
            superR[i] = (short) Math.floor(.5 + 32767 * StrictMath.cos(phase));
            superI[i] = (short) Math.floor(.5 + 32767 * StrictMath.sin(phase));
        }
        factor(HALF);

        filterbank();

        evenSmoothing = (int) (0.025f * (1 << NR_BITS));
        oddSmoothing = (int) (0.06f * (1 << NR_BITS));
        minSignalRemaining = (int) (0.05f * (1 << NR_BITS));

        // PCAN gain lookup table.
        int correctionBits = msb32(FFT) - 1 - (FILTERBANK_BITS / 2);
        snrShift = 21 - correctionBits - SNR_BITS;
        int inputBits = SMOOTHING_BITS - correctionBits;
        gainLut[0] = pcanGain(inputBits, 0);
        gainLut[1] = pcanGain(inputBits, 1);
        for (int interval = 2; interval <= 32; interval++) {
            long x0 = 1L << (interval - 1);
            long x1 = x0 + (x0 >> 1);
            long x2 = interval == 32 ? x0 + (x0 - 1) : 2 * x0;
            short y0 = pcanGain(inputBits, x0);
            short y1 = pcanGain(inputBits, x1);
            short y2 = pcanGain(inputBits, x2);
            int diff1 = y1 - y0;
            int diff2 = y2 - y0;
            int a1 = 4 * diff1 - diff2;
            int a2 = diff2 - a1;
            gainLut[4 * interval - 6] = y0;
            gainLut[4 * interval - 6 + 1] = (short) a1;
            gainLut[4 * interval - 6 + 2] = (short) a2;
        }
    }

    /** Back to silence, as at the start. */
    public void reset() {
        java.util.Arrays.fill(input, (short) 0);
        java.util.Arrays.fill(windowed, (short) 0);
        inputUsed = 0;
        maxAbs = 0;
        java.util.Arrays.fill(fftIn, (short) 0);
        java.util.Arrays.fill(fftR, (short) 0);
        java.util.Arrays.fill(fftI, (short) 0);
        java.util.Arrays.fill(work, 0);
        java.util.Arrays.fill(estimate, 0);
    }

    /**
     * Takes up to {@code count} samples from {@code samples[offset]} and returns how many it used.
     * When that completed a window, {@link #ready} is true and {@link #features} holds a new frame.
     */
    public int process(short[] samples, int offset, int count) {
        ready = false;
        int toCopy = Math.min(SIZE - inputUsed, count);
        System.arraycopy(samples, offset, input, inputUsed, toCopy);
        inputUsed += toCopy;
        if (inputUsed < SIZE) return toCopy;

        short max = 0;
        for (int i = 0; i < SIZE; i++) {
            short v = (short) ((input[i] * coefficients[i]) >> WINDOW_BITS);
            windowed[i] = v;
            short a = v < 0 ? (short) -v : v;
            if (a > max) max = a;
        }
        System.arraycopy(input, STEP, input, 0, SIZE - STEP);
        inputUsed -= STEP;
        maxAbs = max;

        frame();
        ready = true;
        return toCopy;
    }

    /** Whether the last {@link #process} call produced a frame of features. */
    public boolean ready;

    // ---- One frame ----------------------------------------------------------------------

    private void frame() {
        int inputShift = 15 - msb32(maxAbs);
        for (int i = 0; i < SIZE; i++) fftIn[i] = (short) ((windowed[i] & 0xFFFF) << inputShift);
        for (int i = SIZE; i < FFT; i++) fftIn[i] = 0;
        fftr();

        for (int i = startIndex; i < endIndex; i++) {
            int r = fftR[i];
            int im = fftI[i];
            energy[i] = r * r + im * im;
        }

        long weightAcc = 0;
        long unweightAcc = 0;
        for (int ch = 0; ch < CHANNELS + 1; ch++) {
            int m = chanFreqStarts[ch];
            int w = chanWeightStarts[ch];
            int width = chanWidths[ch];
            for (int j = 0; j < width; j++) {
                long mag = energy[m + j];
                weightAcc += weights[w + j] * mag;
                unweightAcc += unweights[w + j] * mag;
            }
            work[ch] = weightAcc;
            weightAcc = unweightAcc;
            unweightAcc = 0;
        }
        for (int i = 0; i < CHANNELS; i++) signal[i] = sqrt64(work[i + 1]) >>> inputShift;

        // Noise reduction
        for (int i = 0; i < CHANNELS; i++) {
            int smoothing = (i & 1) == 0 ? evenSmoothing : oddSmoothing;
            int oneMinus = (1 << NR_BITS) - smoothing;
            int scaledUp = signal[i] << SMOOTHING_BITS;
            int est = (int) ((((scaledUp & U32) * smoothing) + ((estimate[i] & U32) * oneMinus)) >>> NR_BITS);
            estimate[i] = est;
            if (Integer.compareUnsigned(est, scaledUp) > 0) est = scaledUp;
            int floor = (int) (((signal[i] & U32) * minSignalRemaining) >>> NR_BITS);
            int subtracted = (scaledUp - est) >>> SMOOTHING_BITS;
            signal[i] = Integer.compareUnsigned(subtracted, floor) > 0 ? subtracted : floor;
        }

        // PCAN gain control
        for (int i = 0; i < CHANNELS; i++) {
            int gain = wideDynamic(estimate[i]);
            int snr = (int) (((signal[i] & U32) * (gain & U32)) >>> snrShift);
            signal[i] = pcanShrink(snr);
        }

        // Log scale
        int correctionBits = msb32(FFT) - 1 - (FILTERBANK_BITS / 2);
        for (int i = 0; i < CHANNELS; i++) {
            int value = signal[i];
            if (correctionBits < 0) value >>>= -correctionBits;
            else value <<= correctionBits;
            value = Integer.compareUnsigned(value, 1) > 0 ? log(value) : 0;
            features[i] = Integer.compareUnsigned(value, 0xFFFF) < 0 ? value : 0xFFFF;
        }
    }

    // ---- KISS FFT, 16-bit fixed point, radix 4 (512 real points = 256 complex = 4^4) -------

    private void factor(int n) {
        int p = 4;
        double floorSqrt = Math.floor(Math.sqrt(n));
        int k = 0;
        do {
            while (n % p != 0) {
                switch (p) {
                    case 4: p = 2; break;
                    case 2: p = 3; break;
                    default: p += 2; break;
                }
                if (p > floorSqrt) p = n;
            }
            n /= p;
            factors[k++] = p;
            factors[k++] = n;
        } while (n > 1);
        for (int i = 0; i < k; i += 2) {
            if (factors[i] != 4) throw new IllegalStateException("only radix 4 is ported");
        }
    }

    private static short sround(int x) {
        return (short) ((x + (1 << 14)) >> 15);
    }

    private void fixdiv(short[] r, short[] i, int at, int div) {
        int s = 32767 / div;
        r[at] = sround(r[at] * s);
        i[at] = sround(i[at] * s);
    }

    private void work(int fout, int f, int fstride, int fi) {
        int p = factors[fi];
        int m = factors[fi + 1];
        int begin = fout;
        int end = fout + p * m;
        if (m == 1) {
            do {
                tmpR[fout] = fftIn[2 * f];
                tmpI[fout] = fftIn[2 * f + 1];
                f += fstride;
            } while (++fout != end);
        } else {
            do {
                work(fout, f, fstride * p, fi + 2);
                f += fstride;
            } while ((fout += m) != end);
        }
        bfly4(begin, fstride, m);
    }

    private void bfly4(int fout, int fstride, int m) {
        final short[] R = tmpR, I = tmpI;
        int tw1 = 0, tw2 = 0, tw3 = 0;
        int k = m;
        int m2 = 2 * m, m3 = 3 * m;
        do {
            fixdiv(R, I, fout, 4);
            fixdiv(R, I, fout + m, 4);
            fixdiv(R, I, fout + m2, 4);
            fixdiv(R, I, fout + m3, 4);

            short s0r = sround(R[fout + m] * twR[tw1] - I[fout + m] * twI[tw1]);
            short s0i = sround(R[fout + m] * twI[tw1] + I[fout + m] * twR[tw1]);
            short s1r = sround(R[fout + m2] * twR[tw2] - I[fout + m2] * twI[tw2]);
            short s1i = sround(R[fout + m2] * twI[tw2] + I[fout + m2] * twR[tw2]);
            short s2r = sround(R[fout + m3] * twR[tw3] - I[fout + m3] * twI[tw3]);
            short s2i = sround(R[fout + m3] * twI[tw3] + I[fout + m3] * twR[tw3]);

            short s5r = (short) (R[fout] - s1r);
            short s5i = (short) (I[fout] - s1i);
            R[fout] = (short) (R[fout] + s1r);
            I[fout] = (short) (I[fout] + s1i);
            short s3r = (short) (s0r + s2r);
            short s3i = (short) (s0i + s2i);
            short s4r = (short) (s0r - s2r);
            short s4i = (short) (s0i - s2i);
            R[fout + m2] = (short) (R[fout] - s3r);
            I[fout + m2] = (short) (I[fout] - s3i);
            tw1 += fstride;
            tw2 += fstride * 2;
            tw3 += fstride * 3;
            R[fout] = (short) (R[fout] + s3r);
            I[fout] = (short) (I[fout] + s3i);

            R[fout + m] = (short) (s5r + s4i);
            I[fout + m] = (short) (s5i - s4r);
            R[fout + m3] = (short) (s5r - s4i);
            I[fout + m3] = (short) (s5i + s4r);
            ++fout;
        } while (--k != 0);
    }

    private void fftr() {
        work(0, 0, 1, 0);

        short[] tdc = {tmpR[0], tmpI[0]};
        short tdcR = sround(tdc[0] * (32767 / 2));
        short tdcI = sround(tdc[1] * (32767 / 2));
        fftR[0] = (short) (tdcR + tdcI);
        fftR[HALF] = (short) (tdcR - tdcI);
        fftI[HALF] = 0;
        fftI[0] = 0;

        for (int k = 1; k <= HALF / 2; k++) {
            short fpkR = sround(tmpR[k] * (32767 / 2));
            short fpkI = sround(tmpI[k] * (32767 / 2));
            short fpnkR = sround(tmpR[HALF - k] * (32767 / 2));
            short fpnkI = sround(((short) -tmpI[HALF - k]) * (32767 / 2));
            short f1kR = (short) (fpkR + fpnkR);
            short f1kI = (short) (fpkI + fpnkI);
            short f2kR = (short) (fpkR - fpnkR);
            short f2kI = (short) (fpkI - fpnkI);
            short twr = sround(f2kR * superR[k - 1] - f2kI * superI[k - 1]);
            short twi = sround(f2kR * superI[k - 1] + f2kI * superR[k - 1]);
            fftR[k] = (short) ((f1kR + twr) >> 1);
            fftI[k] = (short) ((f1kI + twi) >> 1);
            fftR[HALF - k] = (short) ((f1kR - twr) >> 1);
            fftI[HALF - k] = (short) ((twi - f1kI) >> 1);
        }
    }

    // ---- Filterbank ---------------------------------------------------------------------

    private static float freqToMel(float freq) {
        return 1127.0f * (float) StrictMath.log1p(freq / 700.0f);
    }

    private void filterbank() {
        final int plus1 = CHANNELS + 1;
        final int alignment = 2; // 4-byte alignment of 16-bit indices
        final int block = 4;
        float[] centers = new float[plus1];
        float melLow = freqToMel(125.0f);
        float melHi = freqToMel(7500.0f);
        float spacing = (melHi - melLow) / ((float) plus1);
        for (int i = 0; i < plus1; i++) centers[i] = melLow + (spacing * (i + 1));

        float hzPerSbin = 0.5f * 16000 / ((float) (HALF + 1) - 1);
        startIndex = (int) (1.5f + 125.0f / hzPerSbin);
        endIndex = 0;
        int[] actualStarts = new int[plus1];
        int[] actualWidths = new int[plus1];
        int chanFreqIndexStart = startIndex;
        int weightIndexStart = 0;
        boolean needsZeros = false;
        for (int chan = 0; chan < plus1; chan++) {
            int freqIndex = chanFreqIndexStart;
            while (freqToMel(freqIndex * hzPerSbin) <= centers[chan]) freqIndex++;
            int width = freqIndex - chanFreqIndexStart;
            actualStarts[chan] = chanFreqIndexStart;
            actualWidths[chan] = width;
            if (width == 0) {
                chanFreqStarts[chan] = 0;
                chanWeightStarts[chan] = 0;
                chanWidths[chan] = block;
                if (!needsZeros) {
                    needsZeros = true;
                    for (int j = 0; j < chan; j++) chanWeightStarts[j] += block;
                    weightIndexStart += block;
                }
            } else {
                int alignedStart = (chanFreqIndexStart / alignment) * alignment;
                int alignedWidth = chanFreqIndexStart - alignedStart + width;
                int padded = (((alignedWidth - 1) / block) + 1) * block;
                chanFreqStarts[chan] = (short) alignedStart;
                chanWeightStarts[chan] = (short) weightIndexStart;
                chanWidths[chan] = (short) padded;
                weightIndexStart += padded;
            }
            chanFreqIndexStart = freqIndex;
        }
        weights = new short[weightIndexStart];
        unweights = new short[weightIndexStart];
        for (int chan = 0; chan < plus1; chan++) {
            int frequency = actualStarts[chan];
            int n = actualWidths[chan];
            int offset = frequency - chanFreqStarts[chan];
            int start = chanWeightStarts[chan];
            float denom = chan == 0 ? melLow : centers[chan - 1];
            for (int j = 0; j < n; j++, frequency++) {
                float weight = (centers[chan] - freqToMel(frequency * hzPerSbin)) / (centers[chan] - denom);
                int at = start + offset + j;
                weights[at] = (short) floorf(weight * (1 << FILTERBANK_BITS) + 0.5f);
                unweights[at] = (short) floorf((1.0f - weight) * (1 << FILTERBANK_BITS) + 0.5f);
            }
            if (frequency > endIndex) endIndex = frequency;
        }
    }

    // ---- Integer maths ------------------------------------------------------------------

    private static int msb32(int n) {
        return 32 - Integer.numberOfLeadingZeros(n);
    }

    /** Unsigned 32-bit square root, returned as unsigned 16 bits (C's Sqrt32). */
    private static int sqrt32(int num) {
        if (num == 0) return 0;
        int res = 0;
        int maxBit = 32 - msb32(num);
        maxBit |= 1;
        int bit = 1 << (31 - maxBit);
        int iterations = (31 - maxBit) / 2 + 1;
        while (iterations-- > 0) {
            if (Integer.compareUnsigned(num, res + bit) >= 0) {
                num -= res + bit;
                res = (res >>> 1) + bit;
            } else {
                res >>>= 1;
            }
            bit >>>= 2;
        }
        if (Integer.compareUnsigned(num, res) > 0 && res != 0xFFFF) ++res;
        return res & 0xFFFF;
    }

    /** Unsigned 64-bit square root, returned as unsigned 32 bits (C's Sqrt64). */
    private static int sqrt64(long num) {
        if ((num >>> 32) == 0) return sqrt32((int) num);
        long res = 0;
        int maxBit = Long.numberOfLeadingZeros(num); // 64 - MostSignificantBit64
        maxBit |= 1;
        long bit = 1L << (63 - maxBit);
        int iterations = (63 - maxBit) / 2 + 1;
        while (iterations-- > 0) {
            if (Long.compareUnsigned(num, res + bit) >= 0) {
                num -= res + bit;
                res = (res >>> 1) + bit;
            } else {
                res >>>= 1;
            }
            bit >>>= 2;
        }
        if (Long.compareUnsigned(num, res) > 0 && res != 0xFFFFFFFFL) ++res;
        return (int) res;
    }

    private short pcanGain(int inputBits, long x) {
        float xf = ((float) x) / ((float) (1L << inputBits));
        float gain = ((float) (1L << 21)) * (float) StrictMath.pow(xf + 80.0f, -0.95f);
        if (gain > 0x7FFF) return 0x7FFF;
        return (short) (int) (gain + 0.5f);
    }

    private int wideDynamic(int x) {
        if (Integer.compareUnsigned(x, 2) <= 0) return gainLut[x];
        short interval = (short) msb32(x);
        int base = 4 * interval - 6;
        short frac = (short) (((interval < 11) ? (x << (11 - interval)) : (x >>> (interval - 11))) & 0x3FF);
        int result = (gainLut[base + 2] * frac) >> 5;
        result += gainLut[base + 1] << 5;
        result *= frac;
        result = (result + (1 << 14)) >> 15;
        result += gainLut[base];
        return (short) result;
    }

    private static int pcanShrink(int x) {
        if (Integer.compareUnsigned(x, 2 << SNR_BITS) < 0) {
            return (x * x) >>> (2 + 2 * SNR_BITS - OUTPUT_BITS);
        }
        return (x >>> (SNR_BITS - OUTPUT_BITS)) - (1 << OUTPUT_BITS);
    }

    private static int log2Fraction(int x, int log2x) {
        int frac = (int) ((x & U32) - (1L << log2x));
        if (log2x < 16) frac <<= 16 - log2x;
        else frac >>= log2x - 16;
        int baseSeg = frac >> (16 - 7);
        int segUnit = (1 << 16) >>> 7;
        int c0 = LOG_LUT[baseSeg];
        int c1 = LOG_LUT[baseSeg + 1];
        int segBase = segUnit * baseSeg;
        int relPos = ((c1 - c0) * (frac - segBase)) >> 16;
        return frac + c0 + relPos;
    }

    private static int log(int x) {
        int integer = msb32(x) - 1;
        int fraction = log2Fraction(x, integer);
        int log2 = (integer << 16) + fraction;
        int round = 65536 / 2;
        int loge = (int) ((45426L * (log2 & U32) + round) >>> 16);
        return ((loge << SCALE_SHIFT) + round) >>> 16;
    }

    private static float cosf(float x) {
        return (float) StrictMath.cos(x);
    }

    private static float floorf(float x) {
        return (float) Math.floor(x);
    }
}
