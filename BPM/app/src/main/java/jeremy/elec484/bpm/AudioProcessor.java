package jeremy.elec484.bpm;

import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;

/**
 * Performs audio processing on a track
 */
public class AudioProcessor {

    private static final String outputDirectory = Environment.getExternalStorageDirectory() + File.separator + "BPM" + File.separator;
    private static final String outputFile = "out.wav";

    /**
     * Reads an input file, performs processing, and returns a path to the output file.
     */
    public static String process(final String trackPath, final double adjustmentRatio) throws IOException {
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


        // Create directory for output
        final File root = new File(outputDirectory);
        if (! root.mkdirs()){
            Log.d("AudioProcessor", "Unable to create directory. It's probably already there.");
        }

        // Open file for output
        File output = new File(root, outputFile);
        //File output = File.createTempFile(outputFile, null, root);

        if (output.exists()) {
            // Delete any old file, if there is one
            output.delete();
        }

        Uri outputFileUri = Uri.fromFile(output);

        try {
            InputStream inputStream = new FileInputStream(trackPath);

            // Get header
            WaveHeader waveHeader = new WaveHeader();
            waveHeader.read(inputStream);

            // Get body data
            byte[] inputBytes = new byte[waveHeader.getNumBytes()]; //TODO
            inputStream.read(inputBytes);

            inputStream.close();

            // Get the config information
            waveHeader.setNumBytes(inputBytes.length);
            Config myConfig = androidConfig(waveHeader);

            // Pad input bytes to a power of 2
            inputBytes = padInputBytes(inputBytes,myConfig);

            // Process
            MyAudioStreamTransformer asti = new MyAudioStreamTransformer(myConfig);
            byte[] outputBytes = asti.doConversionsAndTransforming(inputBytes, adjustmentRatio);

            // Update header with new body length
            waveHeader.setNumBytes(outputBytes.length);

            // Write header and body to output stream
            FileOutputStream outStream = new FileOutputStream(output);
            waveHeader.write(outStream);
            outStream.write(outputBytes);
            outStream.close();

        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        Log.d("AudioProcessor", "Converter Complete");

        return outputFileUri.toString();
    }

    private static Config androidConfig(WaveHeader waveHeader) {
        int encodeFrameSizeIn = waveHeader.getBitsPerSample()/8;
        int myInputTotalBytes = waveHeader.getNumBytes();
        int numberOfChannels = waveHeader.getNumChannels();
        ByteOrder byteOrderIn = ByteOrder.LITTLE_ENDIAN; //TODO
        double sampleRateIn = waveHeader.getSampleRate();

        return new Config(encodeFrameSizeIn, myInputTotalBytes, numberOfChannels, byteOrderIn, sampleRateIn);
    }

    private static byte[] padInputBytes( final byte[] originalBytes, Config configIn){
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

        byte[] paddedBytes = new byte[bufPowTwoSize];
        System.arraycopy(originalBytes, 0, paddedBytes, 0, originalBytes.length);
        System.out.println("Original Byte Length: " + originalBytes.length + ", vs Padded Buffer Length: " + paddedBytes.length);
        return paddedBytes;
    }

}
