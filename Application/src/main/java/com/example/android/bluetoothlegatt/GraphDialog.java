package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class GraphDialog extends Dialog {

    public Activity mActivity;
    public Dialog mDialog;
    public Button mYesBtn, mNoBtn;
    private GraphView mGraphView;
    private LineGraphSeries mLineGraphSeries = new LineGraphSeries<>();
    private Random rand = new Random();
    private Object _lock = new Object();
    private Handler mHandler;

    public GraphDialog(Activity a) {
        super(a);
        mActivity = a;
        mHandler = new Handler();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.graph_dialog);
        getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        mGraphView = (GraphView) findViewById(R.id.graph);
        mGraphView.addSeries(mLineGraphSeries);
    }

    private void updateLineGraph(final eegSample sample) {
        Log.w("GraphDialog", "Getting sample...");
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                int rand_y = rand.nextInt(100);
                mLineGraphSeries.appendData(new DataPoint(sample.getTimestamp(), rand_y), false, 90000);
            }
        });
    }

    public void sampleAvailable(eegSample sample) {
        updateLineGraph(sample);
    }

    public void resetData() {
        mLineGraphSeries.resetData(new DataPoint[] {});
    }
}