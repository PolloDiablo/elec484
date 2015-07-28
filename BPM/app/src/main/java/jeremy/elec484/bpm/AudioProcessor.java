package jeremy.elec484.bpm;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

/**
 * Performs audio processing on a track
 */
public class AudioProcessor {

    private static final String outputDirectory = Environment.getExternalStorageDirectory() + File.separator + "BPM" + File.separator;
    private static final String outputFile = "out.wav";
    private static final String TAG = "AudioProcessor";

    /**
     * Reads an input file, performs processing, and returns a path to the output file.
     *
     *
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
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();



            // Get header
            WaveHeader waveHeader = new WaveHeader();
            int headerLength = waveHeader.read(inputStream);
            Config myConfig = androidConfig(waveHeader);

            // Get body data
            byte[] inputBytes = new byte[waveHeader.getNumBytes()];
            //is.read(inputBytes,headerLength,waveHeader.getNumBytes());
            inputStream.read(inputBytes);

            inputStream.close();

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

        // Read the input file in as an audio file
        // TODO
        /*
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
        System.out.println("Output file format: " + AudioSystem.getAudioFileFormat(output));*/

        //final Config configIn = loadConfig(format);
        //MyAudioStreamTransformer asti = new MyAudioStreamTransformer(configIn);
        //byte[] toOutputBytes = asti.doConversionsAndTransforming(inputBytes);




        System.out.println("Done.");
        Log.d("AudioProcessor", "Converter Complete");

        return outputFileUri.toString();
    }

    private static Config androidConfig(WaveHeader waveHeader) {
        int encodeFrameSizeIn = waveHeader.getBitsPerSample()/8;
        int encodeFrameLength = waveHeader.getNumBytes();
        int numberOfChannels = waveHeader.getNumChannels();
        ByteOrder byteOrderIn = ByteOrder.LITTLE_ENDIAN; //TODO
        double sampleRateIn = waveHeader.getSampleRate();

        return new Config(encodeFrameSizeIn, encodeFrameLength, numberOfChannels, byteOrderIn, sampleRateIn);
    }
    /*
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
    }*/
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private static short[] decodeToMemory(int testinput, boolean reconfigure, Context context) throws IOException {
        Resources mResources = context.getResources();
        short [] decoded = new short[0];
        int decodedIdx = 0;
        AssetFileDescriptor testFd = mResources.openRawResourceFd(testinput);
        MediaExtractor extractor;
        MediaCodec codec;
        ByteBuffer[] codecInputBuffers;
        ByteBuffer[] codecOutputBuffers;
        extractor = new MediaExtractor();
        extractor.setDataSource(testFd.getFileDescriptor(), testFd.getStartOffset(),
                testFd.getLength());
        testFd.close();
        assertEquals("wrong number of tracks", 1, extractor.getTrackCount());
        MediaFormat format = extractor.getTrackFormat(0);
        String mime = format.getString(MediaFormat.KEY_MIME);
        assertTrue("not an audio file", mime.startsWith("audio/"));
        codec = MediaCodec.createDecoderByType(mime);
        codec.configure(format, null /* surface */, null /* crypto */, 0 /* flags */);
        codec.start();
        codecInputBuffers = codec.getInputBuffers();
        codecOutputBuffers = codec.getOutputBuffers();
        if (reconfigure) {
            codec.stop();
            codec.configure(format, null /* surface */, null /* crypto */, 0 /* flags */);
            codec.start();
            codecInputBuffers = codec.getInputBuffers();
            codecOutputBuffers = codec.getOutputBuffers();
        }
        extractor.selectTrack(0);
        // start decoding
        final long kTimeOutUs = 5000;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        int noOutputCounter = 0;
        while (!sawOutputEOS && noOutputCounter < 50) {
            noOutputCounter++;
            if (!sawInputEOS) {
                int inputBufIndex = codec.dequeueInputBuffer(kTimeOutUs);
                if (inputBufIndex >= 0) {
                    ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];
                    int sampleSize =
                            extractor.readSampleData(dstBuf, 0 /* offset */);
                    long presentationTimeUs = 0;
                    if (sampleSize < 0) {
                        Log.d(TAG, "saw input EOS.");
                        sawInputEOS = true;
                        sampleSize = 0;
                    } else {
                        presentationTimeUs = extractor.getSampleTime();
                    }
                    codec.queueInputBuffer(
                            inputBufIndex,
                            0 /* offset */,
                            sampleSize,
                            presentationTimeUs,
                            sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                    if (!sawInputEOS) {
                        extractor.advance();
                    }
                }
            }
            int res = codec.dequeueOutputBuffer(info, kTimeOutUs);
            if (res >= 0) {
                //Log.d(TAG, "got frame, size " + info.size + "/" + info.presentationTimeUs);
                if (info.size > 0) {
                    noOutputCounter = 0;
                }
                if (info.size > 0 && reconfigure) {
                    // once we've gotten some data out of the decoder, reconfigure it again
                    reconfigure = false;
                    extractor.seekTo(0, MediaExtractor.SEEK_TO_NEXT_SYNC);
                    sawInputEOS = false;
                    codec.stop();
                    codec.configure(format, null /* surface */, null /* crypto */, 0 /* flags */);
                    codec.start();
                    codecInputBuffers = codec.getInputBuffers();
                    codecOutputBuffers = codec.getOutputBuffers();
                    continue;
                }
                int outputBufIndex = res;
                ByteBuffer buf = codecOutputBuffers[outputBufIndex];
                if (decodedIdx + (info.size / 2) >= decoded.length) {
                    decoded = Arrays.copyOf(decoded, decodedIdx + (info.size / 2));
                }
                for (int i = 0; i < info.size; i += 2) {
                    decoded[decodedIdx++] = buf.getShort(i);
                }
                codec.releaseOutputBuffer(outputBufIndex, false /* render */);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "saw output EOS.");
                    sawOutputEOS = true;
                }
            } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = codec.getOutputBuffers();
                Log.d(TAG, "output buffers have changed.");
            } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat oformat = codec.getOutputFormat();
                Log.d(TAG, "output format has changed to " + oformat);
            } else {
                Log.d(TAG, "dequeueOutputBuffer returned " + res);
            }
        }
        codec.stop();
        codec.release();
        return decoded;
    }
}
