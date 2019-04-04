package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import com.jjoe64.graphview.GraphView;

public class GraphDialog extends Dialog {

    public Activity mActivity;
    public Dialog mDialog;
    public Button mYesBtn, mNoBtn;
    private GraphView mGraphView;

    public GraphDialog(Activity a) {
        super(a);
        this.mActivity = a;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.graph_dialog);
        getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        mGraphView = (GraphView) findViewById(R.id.graph);
    }
}