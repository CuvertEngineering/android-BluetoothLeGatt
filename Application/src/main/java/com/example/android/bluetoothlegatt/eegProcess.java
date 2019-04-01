//package com.example.android.bluetoothlegatt;
//package org.apache.commons.collections4.queue;
//
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Queue;
//import org.apache.commons.collections4.BoundedCollection;
//
//public class eegProcess {
//    // We need 2 buffers,
//    // one for plotting (that another thread will pull at ODR rate)
//    // one for all the processing (impedance, rate etc...)
//    private EvictingQueue<eegSample> queue = EvictingQueue.create(1000);
//
//    public Int getRate() {
//        // calculate rate between sample n and (n-20)
//    }
//
//    public Int getImpedance() {
//        // calculate min/max over one loff periode (37.5hz @ specific ODR)
//    }
//
//    public void add(eegSample sample) {
//        // add sample to both buffers
//    }
//
//
//}
