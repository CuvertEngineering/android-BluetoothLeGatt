package com.example.android.bluetoothlegatt;
import java.util.ArrayList;
import java.util.List;

public class eegSample {
    private int timestamp;
    private int sampleNum;
    private int chanMask;
    private int odr;
    private List<Integer> channels = new ArrayList<>();

    public eegSample(int sampleTimestamp, int sampleNumber, int channelMask, final List<Integer> channelVals)
    {
        timestamp = sampleTimestamp;
        sampleNum = sampleNumber;
        chanMask = channelMask;
        channels = channelVals;
    }
    public int getTimestamp()
    {
        return timestamp;
    }
    public int getNumberChan()
    {
        return Integer.bitCount(chanMask);
    }
    public int getSingleChan()
    {
        if (getNumberChan() != 1) {
            return 0;
        } else {
            return channels.get(0);

        }
    }
    public int getTimeDiff(int time)
    {
        return timestamp - time;
    }
}
