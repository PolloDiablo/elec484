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
    public byte[] doConversionsAndTransforming(byte[] buffer, final double adjustmentRatio) {

        final int windowSize = 1024;
        //final int R_a = (int) Math.floor(windowSize * 0.03125);
        final int R_a = (int) Math.floor(windowSize * 0.125);
        final int R_s = (int) (R_a * adjustmentRatio);

        if (config.numberOfChannels == 1) {
            // Just deal with one channel as normal
            double[] toTransform = toMonoDoubleArray(buffer);
            buffer = null; //MEMORY?
            double[] toOutputDouble = phasevocoder(toTransform, windowSize, R_a, R_s);
            toTransform = null; //MEMORY?
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
            buffer = null; //MEMORY?

            // Perform computations

            double[] leftToTransform = toMonoDoubleArray(bufferLeft);
            bufferLeft = null; //MEMORY?
            double[] leftToOutputDouble = phasevocoder(leftToTransform,windowSize, R_a, R_s);
            leftToTransform = null; //MEMORY?
            byte[] leftToOutputBytes = fromMonoDoubleArray(leftToOutputDouble);
            leftToOutputDouble = null; //MEMORY?

            double[] rightToTransform = toMonoDoubleArray(bufferRight);
            bufferRight = null; //MEMORY?
            double[] rightToOutputDouble = phasevocoder(rightToTransform, windowSize, R_a, R_s);
            rightToTransform = null; //MEMORY?
            byte[] rightToOutputBytes = fromMonoDoubleArray(rightToOutputDouble);
            rightToOutputDouble = null; //MEMORY?

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
            leftToOutputBytes = null; //MEMORY?
            rightToOutputBytes = null; //MEMORY?

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
        double[] windowTime = new double[windowSize];
        Complex[] windowZpf;
        double[] previousPhases = new double[windowSize];
        double[] currentPhases = new double[windowSize];
        double[] phaseChanges = new double[windowSize];
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

        // Apply windowing
        // Increment by R_a (analysis hop size)
        // Stop before the last WINDOW_SIZE, so we don't read out-of-bounds
        int analysisWindowStart = 0;
        int synthesisWindowStart = 0;
        double twoPI = 2.0 * Math.PI;
        double hanningConstant = twoPI / windowSize;

        for (int windowIndex = 0; windowIndex < totalWindows; windowIndex++) {

            // Perform Analysis (get the Window)
            // The window is a hanning window, so apply this function
            int index = analysisWindowStart;
            for (int n = 0; n < windowSize; n++) {
                windowTime[n] = x[index] * 0.5 * (1.0 - Math.cos(n * hanningConstant));
                index++;
            }

            // Apply circular shift to window in time domain
            windowTime = circularShift(windowTime);

            // Get FFT of window
            windowZpf = transformer.transform(windowTime, TransformType.FORWARD);

            // =========================
            // Time-Frequency Processing

            // Get the magnitude and phases
            System.arraycopy(currentPhases, 0, previousPhases, 0, windowSize);

            for (int i = 0; i < windowSize; i++) {
                currentPhases[i] = windowZpf[i].getArgument();
                // Get the phase of the previous window and the current window
                if (firsttime) {
                    // First window: output phase
                    yPhases[i] = currentPhases[i];
                } else {
                    // Any other cycle

                    // Calculate phase change between current and previous window
                    phaseChanges[i] = currentPhases[i] - previousPhases[i];

                    // Unwrap the phase change
                    int number = (int) Math.round((phaseChanges[i] - windowUnwrapper[i]) / (twoPI));
                    phaseChanges[i] = phaseChanges[i] - number * twoPI;

                    // Adjust phase by hopRatio (R_s/R_a) to do time stretching
                    phaseChanges[i] = hopRatio * phaseChanges[i];

                    // The phase of y keeps increasing (add to y from previous)
                    yPhases[i] = yPhases[i] + phaseChanges[i];
                }

                // Get y values
                windowZpf[i] = new Complex(windowZpf[i].abs() * Math.cos(yPhases[i]), windowZpf[i].abs() * Math.sin(yPhases[i]));
            }
            // =========================

            // Inverse FFT of window
            windowZpf = transformer.transform(windowZpf, TransformType.INVERSE);
            for (int i = 0; i < windowZpf.length; i++) {
                windowTime[i] = windowZpf[i].getReal(); // abs()?
            }

            // Undo the circular shift in time domain
            windowTime = circularShift(windowTime);

            // Synthesis
            index = synthesisWindowStart;
            for (int n = 0; n < windowSize; n++) {
                output[index] = output[index] + windowTime[n];
                index++;
            }

            // Get window bounds for next window
            analysisWindowStart += R_a;
            synthesisWindowStart += R_s;
            firsttime = false;
        }

        // Adjust for window overlap
        for (int i = 0; i < outputLength; i++) {
            output[i] = output[i] * analysisAmplitudeScale * hopRatio;
        }

        return output;
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
    /*
    private short[] fromMonoDoubleArray(final double[] toOutputDouble) {
        short[] toReturn = new short[toOutputDouble.length];
        for (int i = 0; i < toOutputDouble.length; i ++) {
            toReturn[i] = (short) toOutputDouble[i];
        }
        return toReturn;
    }*/

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

    /**
     * Circular shift by windowSize/2
     */
    private double[] circularShift(double[] input) {
        double[] y = new double[input.length];
        int writeIndex = input.length / 2;
        for (double aX : input) {
            y[writeIndex] = aX;
            writeIndex = (writeIndex + 1) % input.length;
        }
        return y;
    }


}
