package com.example.android.bluetoothlegatt;
import java.util.ArrayList;
import java.util.List;

public class eegSample {
    private int timestamp;
    private int sampleNum;
    private int chanMask;
    private List<Integer> channels = new ArrayList<>();

    public eegSample(int sampleTimestamp, int sampleNumber, int channelMask, final List<Integer> channelVals) {
        timestamp = sampleTimestamp;
        sampleNum = sampleNumber;
        chanMask = channelMask;
        channels = channelVals;
    }
}
