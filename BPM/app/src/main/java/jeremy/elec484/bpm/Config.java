package jeremy.elec484.bpm;

import java.nio.ByteOrder;

/**
 * Created by Jeremy on 7/26/2015.
 */
public class Config {
    public final int encodeFrameSize;
    private final int encodeFrameLength;
    public final int numberOfChannels;
    public final ByteOrder byteOrder;
    public final double sampleRate;

    /**
     * myFrameSize = encodeFrameSize/numberOfChannels; This is because a single
     * encoded frame contains multiple channels
     */
    public final int monoFrameSize;

    /**
     * myInputTotalBytes = encodeFrameLength*encodeFrameSize;
     *
     * (Bytes) = (Frames)*(Bytes/Frame)
     */
    public final int myInputTotalBytes;

    /**
     * Create config for the transformer to use.
     *
     * @param encodeFrameSizeIn
     * @param byteOrderIn
     * @param sampleRateIn
     */
    public Config(final int encodeFrameSizeIn, final int encodeFrameLength,
                  int numberOfChannels, final ByteOrder byteOrderIn,
                  final double sampleRateIn) {

        this.encodeFrameSize = encodeFrameSizeIn;
        this.encodeFrameLength = encodeFrameLength;
        this.numberOfChannels = numberOfChannels;
        this.byteOrder = byteOrderIn;
        this.sampleRate = sampleRateIn;

        monoFrameSize = encodeFrameSize / numberOfChannels;
        myInputTotalBytes = encodeFrameLength * encodeFrameSize;
    }

	/*
	 * Generated.
	 */

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "Config [encodeFrameSize=" + encodeFrameSize + ", byteOrder="
                + byteOrder + ", sampleRate=" + sampleRate + "]";
    }
}
