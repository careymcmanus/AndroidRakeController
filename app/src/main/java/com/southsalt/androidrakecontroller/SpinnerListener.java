package com.southsalt.androidrakecontroller;

import android.util.Log;
import android.view.View;
import android.widget.AdapterView;

import static android.content.ContentValues.TAG;

public class SpinnerListener implements AdapterView.OnItemSelectedListener {
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Log.i(TAG, "onItemSelected: ");
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }
}
