package jeremy.elec484.bpm;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

/**
 * Transforms an Audio Stream
 */
public class MyAudioStreamTransformer {

    /**
     * The config.
     */
    private final Config config;

    double ratio = 2.0;
    int windowSize = 2048;
    int R_a = (int) Math.floor(windowSize * 0.03125);
    int R_s = (int) (R_a * ratio);

    /**
     * Create audio stream transformer, with the given configuration.
     *
     */
    public MyAudioStreamTransformer(final Config configIn) {
        this.config = configIn;
        System.out.println("Audio Stream Transformer initialized with config: " + config);
    }

    /**
     * Take things into and then out of the frequency domain.
     */
    public byte[] doConversionsAndTransforming(final byte[] buffer) {

        if (config.numberOfChannels == 1) {
            // Just deal with one channel as normal
            double[] toTransform = toMonoDoubleArray(buffer);
            // windowSize = toTransform.length / 2;
            // R_a = toTransform.length / 2;
            // R_s = toTransform.length / 2;
            double[] toOutputDouble = phasevocoder(toTransform, windowSize, R_a, R_s);
            return fromMonoDoubleArray(toOutputDouble);

        } else if (config.numberOfChannels == 2) {

            // Split channels
            byte[] bufferLeft = new byte[(buffer.length / 2)];
            byte[] bufferRight = new byte[(buffer.length / 2)];

            // Iterate through each full frame of the input (generally 4 bytes
            // at a time)
            for (int frameIndex = 0; frameIndex < buffer.length / config.encodeFrameSize - 1; frameIndex++) {

                int fullFrameStart = frameIndex * config.encodeFrameSize;
                int monoFrameStart = fullFrameStart / 2;
                for (int innerIndex = 0; innerIndex < config.monoFrameSize; innerIndex++) {
                    bufferLeft[monoFrameStart + innerIndex] = buffer[fullFrameStart + innerIndex];
                    bufferRight[monoFrameStart + innerIndex] = buffer[fullFrameStart + config.monoFrameSize + innerIndex];
                }
            }

            // Perform computations

            double[] leftToTransform = toMonoDoubleArray(bufferLeft);
            // double[] leftToOutputDouble = transformCode(leftToTransform, co);
            double[] leftToOutputDouble = phasevocoder(leftToTransform,windowSize, R_a, R_s);

            byte[] leftToOutputBytes = fromMonoDoubleArray(leftToOutputDouble);

            double[] rightToTransform = toMonoDoubleArray(bufferRight);
            // double[] rightToOutputDouble = transformCode(rightToTransform,
            // co);
            double[] rightToOutputDouble = phasevocoder(rightToTransform, windowSize, R_a, R_s);
            byte[] rightToOutputBytes = fromMonoDoubleArray(rightToOutputDouble);

            // Recombine channels
            // Note: output size NOT necessarily equal to input size
            // This is because of the windowing done in the phase vocoder

            int outputLength = leftToOutputBytes.length + rightToOutputBytes.length;

            byte[] toOutputBytes = new byte[outputLength];
            for (int frameIndex = 0; frameIndex < outputLength / config.encodeFrameSize - 1; frameIndex++) {
                int fullFrameStart = frameIndex * config.encodeFrameSize;
                int monoFrameStart = fullFrameStart / 2;
                for (int innerIndex = 0; innerIndex < config.monoFrameSize; innerIndex++) {
                    toOutputBytes[fullFrameStart + innerIndex] = leftToOutputBytes[monoFrameStart + innerIndex];
                    toOutputBytes[fullFrameStart + config.monoFrameSize + innerIndex] = rightToOutputBytes[monoFrameStart + innerIndex];
                }

            }

            return toOutputBytes;

        } else {
            throw new RuntimeException("Unsupported number of channels [" + config.numberOfChannels + "]");
        }
    }

    /**
     * Perform the FFT transforming.
     *
     * @param x
     *            - input signal
     * @param windowSize
     *            - Size of the window to use on x (must be an even, positive,
     *            integer)
     * @param R_a
     *            - Size of the hop to use during Analysis
     * @param R_s
     *            - Size of the hop to use during Synthesis
     * @return computed result
     */
    private double[] phasevocoder(final double[] x, final int windowSize, final int R_a, final int R_s) {

        System.out.println("Beginning Phase Vocoder...\n");

        // Constrain Input to non-error bounds
        // WINDOW_SIZE must be an even number
        // WINDOW_SIZE must be greater than zero
        // R_a must be greater than zero
        // R_s must be greater than zero
        if (windowSize % 2 != 0 || windowSize < 1 || R_a < 1 || R_s < 1) {
            throw new RuntimeException("ERROR: invalid input\n");
        }

        // Constrain Input to reasonable bounds
        // For audio:
        // If WINDOW_SIZE is too large, we get an echoing effect
        // If WINDOW_SIZE is too small, we get frequency aliasing
        // If R_a is > WINDOW_SIZE/2, the windows do not fully overlap
        // Ideally, R_a should be ~ 1/16 WINDOW_SIZE
        // If R_a is too small, computation takes a very long time
        // If R_s is > WINDOW_SIZE/2, the windows do not fully overlap
        // R_s should be within a factor of 4 of R_a
        if (windowSize > 4096 || windowSize < 256 || R_a > windowSize / 8 || R_a < windowSize / 128 || R_s > windowSize / 2
                || R_s > 4 * R_a || R_s < 0.25 * R_a) {
            System.out.println("WARNING: poor inputs chosen for audio\n");
        }

        // Number of windows based on analysis hop size
        int totalWindows = (int) Math.ceil((x.length - windowSize + 1) / (double) (R_a));

        // memory allocation for output
        // Length of output depends on the ratio between hop sizes
        // i.e. the stretch_factor
        int outputLength = windowSize + (totalWindows - 1) * R_s;
        double[] output = new double[outputLength];

        // R_a == WINDOW_SIZE/2, then analysis scaling factor is 1
        // Otherwise, there is more or less overlap between each window
        double analysisAmplitudeScale = (R_a) / (windowSize * 0.5);

        // R_s == R_a, then synthesis scaling factor is 1
        // R_s == 2*R_a, then synthesis scaling factor is 2
        double hopRatio = (double) (R_s) / (double) (R_a);

        // For the phase vocoding
        // Store the phase component of each sample in the window
        double[] currentXPhases = new double[windowSize];
        double[] yPhases = new double[windowSize];

        // We cannot calculate the phase change on the 1st window
        boolean firsttime = true;
        // Phases wrap-around at 2pi by default
        // We need to unwrap the phases (to get a linearly increasing line)
        double phaseOffsetIncreasePerSample = 2.0 * Math.PI * ((double) (R_a) / (double) (windowSize));
        double[] windowUnwrapper = new double[windowSize];
        for (int i = 0; i < windowSize; i++) { // Generate: [1 2 3 4...]
            windowUnwrapper[i] = phaseOffsetIncreasePerSample * (1 + i);
        }

        // For FFT later
        FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);

        // TODO optimize!

        // Apply windowing
        // Increment by R_a (analysis hop size)
        // Stop before the last WINDOW_SIZE, so we don't read out-of-bounds
        for (int windowIndex = 0; windowIndex < totalWindows; windowIndex++) {
            // Get window bounds
            int analysisWindowStart = windowIndex * R_a;
            int synthesisWindowStart = windowIndex * R_s;

            // Analysis
            double[] xWindow = new double[windowSize];
            int index = analysisWindowStart;
            for (int n = 0; n < windowSize; n++) {
                xWindow[n] = x[index];

                index++;
            }

            xWindow = hanningWindow(xWindow);

            // Apply circular shift to windows in time domain
            double[] xWindowShifted = circularShift(xWindow);

            // Get FFT of window
            Complex[] xWindowZpfShifted = transformer.transform(xWindowShifted, TransformType.FORWARD);

            // =========================
            // Time-Frequency Processing

            // Get the magnitude
            double[] xMagnitudes = new double[windowSize];

            for (int i = 0; i < windowSize; i++) {
                xMagnitudes[i] = xWindowZpfShifted[i].abs();
            }

            // Get the phase of the previous window and the current window
            double[] previousXPhases = new double[windowSize];
            System.arraycopy(currentXPhases, 0, previousXPhases, 0, windowSize);

            for (int i = 0; i < windowSize; i++) {
                currentXPhases[i] = xWindowZpfShifted[i].getArgument();
            }

            if (firsttime) {
                // First window: output phase
                System.arraycopy(currentXPhases, 0, yPhases, 0, windowSize);
                firsttime = false;
            } else {
                // Calculate phase change between current and previous window
                double[] phaseChanges = new double[windowSize];
                for (int i = 0; i < windowSize; i++) {
                    phaseChanges[i] = currentXPhases[i] - previousXPhases[i];
                }

                // Unwrap the phase change
                double[] unwrappedPhaseChanges = new double[windowSize];
                for (int i = 0; i < windowSize; i++) {
                    int number = (int) Math.round((phaseChanges[i] - windowUnwrapper[i]) / (2.0 * Math.PI));
                    unwrappedPhaseChanges[i] = phaseChanges[i] - number * 2.0 * Math.PI;
                }

                // Adjust phase by hopRatio (R_s/R_a) to do time stretching
                double[] yPhaseAdjusted = new double[windowSize];
                for (int i = 0; i < windowSize; i++) {
                    yPhaseAdjusted[i] = hopRatio * unwrappedPhaseChanges[i];
                }

                // The phase of y keeps increasing (add to y from previous)
                for (int i = 0; i < windowSize; i++) {
                    yPhases[i] = yPhases[i] + yPhaseAdjusted[i];
                }

            }
            // =========================
            Complex[] yWindowZpfShifted = new Complex[windowSize];
            for (int i = 0; i < windowSize; i++) {
                yWindowZpfShifted[i] = new Complex(xMagnitudes[i] * Math.cos(yPhases[i]), xMagnitudes[i] * Math.sin(yPhases[i]));
            }

            // Inverse FFT of window
            Complex[] yWindowShifted = transformer.transform(yWindowZpfShifted, TransformType.INVERSE);
            double[] yWindow = toRealOnly(yWindowShifted);

            // Undo the circular shift in time domain
            yWindow = circularShift(yWindow);

            // Synthesis
            index = synthesisWindowStart;
            for (int n = 0; n < windowSize; n++) {
                output[index] = output[index] + yWindow[n];
                index++;
            }

        }

        // Adjust for window overlap
        for (int i = 0; i < outputLength; i++) {
            output[i] = output[i] * analysisAmplitudeScale * hopRatio;
        }

        // Normalize, if necessary TODO
		/*
		 * double max = getMax(output); double min = getMin(output);
		 * System.out.println("Old max: " + max); if (max > 1) { for (int i = 0;
		 * i < outputLength; i++) { output[i] = (output[i] - min) / (max - min);
		 * } } System.out.println("New max: " + getMax(output));
		 */
        return output;
    }

    /**
     * Perform the FFT transforming.
     *
     * @param toTransform
     * @param co
     * @return
     */
	/*
	 * private double[] transformCode(final double[] toTransform, final
	 * ComplexOperator co) {
	 *
	 * System.out.println(); System.out.println("Transforming...");
	 * System.out.println("Before transform length = " + toTransform.length);
	 *
	 * // FFT it FastFourierTransformer transformer = new
	 * FastFourierTransformer(DftNormalization.STANDARD); Complex[] toMathData =
	 * transformer.transform(toTransform, TransformType.FORWARD);
	 * System.out.println("FFT output length = " + toMathData.length);
	 *
	 * // Do something with the data. Complex[] mathedData =
	 * co.doMath(toMathData, config);
	 *
	 * // Now transform it back Complex[] toOutputComplex =
	 * transformer.transform(mathedData, TransformType.INVERSE); // Should be
	 * all real? double[] toOutputDouble = toRealOnly(toOutputComplex);
	 *
	 * System.out.println(); return toOutputDouble; // Time-return
	 * DelayBasedFilter.doMath(toTransform, config); }
	 */

    /**
     * Keep the real pieces only of the recreated signal.
     */
    private static double[] toRealOnly(final Complex[] toOutputComplex) {
        double[] toReturn = new double[toOutputComplex.length];
        for (int i = 0; i < toOutputComplex.length; i++) {
            toReturn[i] = toOutputComplex[i].getReal(); // abs()?
        }
        return toReturn;
    }

    /*********************************************/
	/*
	 * DATA CONVERSION To/From Bytes and Doubles
	 */
    /*********************************************/

    /**
     * Based on the configuration encoding frame size and endianness. Works on a
     * mono array.
     */
    private byte[] fromMonoDoubleArray(final double[] toOutputDouble) {
        byte[] toReturn = new byte[toOutputDouble.length * config.monoFrameSize];
        for (int i = 0; i < toOutputDouble.length; i++) {
            ByteBuffer bb = ByteBuffer.allocate(config.monoFrameSize);
            bb.order(config.byteOrder);
            if (config.monoFrameSize == 4) {
                int valToConvert = (int) toOutputDouble[i];
                bb.putInt(valToConvert);
            } else if (config.monoFrameSize == 2) {
                short valToConvert = (short) toOutputDouble[i];
                bb.putShort(valToConvert);
            } else {
                throw new RuntimeException("Unsupported byte amount [" + config.monoFrameSize + "]");
            }
            bb.rewind();
            byte[] arr = bb.array();
            toReturn[i * config.monoFrameSize] = arr[0];
            toReturn[i * config.monoFrameSize + 1] = arr[1];
            if (2 < config.monoFrameSize) {
                toReturn[i * config.monoFrameSize + 2] = arr[2];
            }
            if (3 < config.monoFrameSize) {
                toReturn[i * config.monoFrameSize + 3] = arr[3];
            }
        }
        return toReturn;
    }

    /**
     * Based on the configuration encoding frame size and endianness. Works on a
     * mono array.
     */
    private double[] toMonoDoubleArray(final byte[] buffer) {
        double[] toReturn = new double[buffer.length / config.monoFrameSize];
        for (int i = 0; i < buffer.length; i += config.monoFrameSize) {
            byte[] toUse = Arrays.copyOfRange(buffer, i, i + config.monoFrameSize);
            ByteBuffer bb = ByteBuffer.allocate(config.monoFrameSize);
            bb.order(config.byteOrder);
            bb.put(toUse);
            bb.rewind();
            if (config.monoFrameSize == 4) {
                toReturn[i / config.monoFrameSize] = bb.getInt();
            } else if (config.monoFrameSize == 2) {
                toReturn[i / config.monoFrameSize] = bb.getShort();
            } else {
                throw new RuntimeException("Unsupported byte amount [" + config.monoFrameSize + "]");
            }
        }
        return toReturn;
    }

    /*
     * Applies HanningWindow to input x
     */
    private double[] hanningWindow(final double[] x) {
        double[] y = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            y[i] = x[i] * 0.5 * (1.0 - Math.cos(i * 2.0 * Math.PI / x.length));
        }
        return y;
    }

    /**
     * Returns max value in array
     */
    private double getMax(final double[] x) {
        double max = Double.MIN_VALUE;
        for (int i = 0; i < x.length; i++) {
            if (x[i] > max) {
                max = x[i];
            }
        }
        return max;
    }

    /**
     * Returns min value in array
     */
    private double getMin(final double[] x) {
        double min = Double.MAX_VALUE;
        for (int i = 0; i < x.length; i++) {
            if (x[i] < min) {
                min = x[i];
            }
        }
        return min;
    }

    /**
     * Circular shift by windowSize/2
     */
    private double[] circularShift(final double[] x) {
        double[] y = new double[x.length];
        int writeIndex = x.length / 2;
        for (double aX : x) {
            y[writeIndex] = aX;
            writeIndex = (writeIndex + 1) % x.length;
        }
        return y;
    }


}
