package jeremy.elec484.bpm;

import android.os.Environment;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;

import myjavax.sound.sampled.AudioFileFormat;
import myjavax.sound.sampled.AudioInputStream;
import myjavax.sound.sampled.AudioSystem;
import myjavax.sound.sampled.UnsupportedAudioFileException;

/**
 * Performs audio processing on a track
 */
public class AudioProcessor {

    private static final String pathToOutput = Environment.getExternalStorageDirectory() + File.separator + "Divi" + File.separator + "out.wav";

    /**
     * Reads an input file, performs processing, and returns a path to the output file.
     *
     *
     */
    public static String process(final String trackPath, final double adjustmentRatio) throws UnsupportedAudioFileException, IOException {
        Log.d("AudioProcessor", "Starting Converter");
        System.out.println("Starting Audio Converter");


        if (adjustmentRatio<0 || adjustmentRatio > 10) {
            Log.e("AudioProcessor", "ERROR: invalid adjustmentRatio");
            throw new IOException();
        }

        File input = new File(trackPath);
        if (!input.exists()) {
            Log.e("AudioProcessor", "ERROR: file [" + input.getAbsolutePath() + "] not openable");
            throw new IOException();
        }

        File output = new File(pathToOutput);
        if (!output.exists()) {
            // Delete any old file, if there is one
            output.delete();
        }

        // Read the input file in as an audio file
        // TODO
        System.out.println("Reading in: " + input.getAbsolutePath());
        final AudioFileFormat format = AudioSystem.getAudioFileFormat(input);
        System.out.println("Input file format: " + format.toString());
        final AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(input);

        final Config configIn = loadConfig(format);
        final byte[] inputBytes = loadBytes(audioInputStream, configIn);

        // Transform it
        MyAudioStreamTransformer asti = new MyAudioStreamTransformer(configIn);
        byte[] toOutputBytes = asti.doConversionsAndTransforming(inputBytes);

        // Write it out
        ByteArrayInputStream bias = new ByteArrayInputStream(toOutputBytes);

        AudioInputStream audioInputStream2ForOutput = new AudioInputStream(bias, format.getFormat(), format.getFrameLength());
        int outByteCount = AudioSystem.write(audioInputStream2ForOutput, AudioFileFormat.Type.WAVE, output);
        System.out.println("Wrote out " + outByteCount + " bytes, to file: " + output.getAbsolutePath());
        System.out.println("Output file format: " + AudioSystem.getAudioFileFormat(output));


        System.out.println("Done.");
        Log.d("AudioProcessor", "Converter Complete");

        return pathToOutput;
    }

    private static Config loadConfig(final AudioFileFormat format) {
        int encodeFrameSizeIn = format.getFormat().getFrameSize();
        int encodeFrameLength = format.getFrameLength();
        int numberOfChannels = format.getFormat().getChannels();

        ByteOrder byteOrderIn = format.getFormat().isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        double sampleRateIn = format.getFormat().getSampleRate();
        return new Config(encodeFrameSizeIn, encodeFrameLength, numberOfChannels, byteOrderIn, sampleRateIn);
    }

    private static byte[] loadBytes(final AudioInputStream audioInputStream, Config configIn) throws UnsupportedAudioFileException,
            IOException {

        final int bufPowTwoSize;
        if (configIn.numberOfChannels == 1) {
            // One channel
            bufPowTwoSize = (int) Math.pow(2, 1 + (int) (Math.log(configIn.myInputTotalBytes) / Math.log(2)));
        } else if ((configIn.numberOfChannels == 2)) {
            // If there are two channels, then we will be analyzing half the
            // buffer at a time
            // Hence the entire buffer must be size double a power of two
            bufPowTwoSize = (int) (2 * Math.pow(2, 1 + (int) (Math.log(configIn.myInputTotalBytes) / Math.log(2))));
        } else {
            throw new RuntimeException("Unsupported number of channels [" + configIn.numberOfChannels + "]");
        }

        byte[] inputBytes = new byte[bufPowTwoSize];
        final int readLength = audioInputStream.read(inputBytes);
        System.out.println("Byte Length: " + readLength + ", vs Buffer Length: " + inputBytes.length);
        return inputBytes;
    }
}
