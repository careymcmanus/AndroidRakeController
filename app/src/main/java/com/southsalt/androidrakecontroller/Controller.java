package com.southsalt.androidrakecontroller;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import static android.content.ContentValues.TAG;

public class Controller extends Fragment implements ServiceConnection, SerialListener {


    private String mDeviceAddress;
    private String mDeviceName;
    private TextView mDeviceNameText;
    private TextView mConnectedText;
    private Button mRefreshButton;
    private TextView mReceiveText;
    private View mSendBtn;
    private View mJogFwdBtn;
    private View mJogBackBtn;
    private View mStartBtn;
    private View mStopBtn;

    private enum Connected {False, Pending, True}


    private String newline = "\r\n";
    /*
     * Command Recieved Flag
     */
    private boolean newData = false;
    private String command;
    /*
     * Motor State Variables
     */
    private List<MotorState> motorStates = new ArrayList<>();
    private int currentState;

    private SerialSocket socket;
    private SerialService service;
    private boolean initialStart = true;
    private Connected connected = Connected.False;

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        mDeviceAddress = getArguments().getString("address");
        mDeviceName = getArguments().getString("name");
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(Context activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);

    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart && service !=null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }


    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

        service = null;

    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.controller_layout, container, false);
        mReceiveText = view.findViewById(R.id.receive_text);
        mReceiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans

        mConnectedText = view.findViewById(R.id.connected_label);
        mDeviceNameText = view.findViewById(R.id.device_name_label);
        mSendBtn = view.findViewById(R.id.refresh_btn);
        mJogFwdBtn = view.findViewById(R.id.fwd_btn);
        mJogBackBtn = view.findViewById(R.id.back_btn);
        mStartBtn = view.findViewById(R.id.start_btn);
        mStopBtn = view.findViewById(R.id.stop_btn);


        /*
         * Set any text that is dynamic
         */
        mDeviceNameText.setText(getString(R.string.device_name)+mDeviceName);
        updateConnectedText();

        /*
         * Set the on click listeners for the various buttons
         */
        mSendBtn.setOnClickListener(v -> getStates());
        mStartBtn.setOnClickListener(v -> send(getString(R.string.start_cmd)));
        mStopBtn.setOnClickListener(v -> send(getString(R.string.stop_cmd)));




        mJogFwdBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                jogCommand(event, true); // set direction true for forward
                return false;
            }
        });

        mJogBackBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                jogCommand(event, false); //pass direction=false for backwards.
                return false;
            }
        });


        return view;
    }

    public void jogCommand(MotionEvent event, boolean direction){
        switch (event.getAction()){
            case MotionEvent.ACTION_DOWN:
                if(direction)
                    send(getString(R.string.jog_fwd_cmd));
                else
                    send(getString(R.string.jog_bck_cmd));
                return;
            case MotionEvent.ACTION_UP:
                send(getString(R.string.jog_stop_cmd));
        }

    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            mReceiveText.setText("");
            return true;
        } else if (id ==R.id.newline) {
            String[] newlineNames = getResources().getStringArray(R.array.newline_names);
            String[] newlineValues = getResources().getStringArray(R.array.newline_values);
            int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Newline");
            builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
                newline = newlineValues[item1];
                dialog.dismiss();
            });
            builder.create().show();
            return true;
        } else if(id == R.id.Reconnect){
            connect();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(mDeviceAddress);
            String deviceName = device.getName() != null ? device.getName() : device.getAddress();
            status("connecting...");
            connected = Connected.Pending;
            socket = new SerialSocket();
            service.connect(this, "Connected to " + deviceName);
            socket.connect(getContext(), service, device);
            updateConnectedText();


        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
        socket.disconnect();
        socket = null;
        updateConnectedText();
    }

    private void send(String str) {
        if(connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            SpannableStringBuilder spn = new SpannableStringBuilder(str+'\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            mReceiveText.setText(spn);
            byte[] data = (str + newline).getBytes();
            socket.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(byte[] data) {

        String dataString = new String(data);
        mReceiveText.setText(new String(data));
        StringBuilder stringBuilder = new StringBuilder();
        if (dataString.startsWith("<")) {
            stringBuilder.append(dataString);
            Log.i(TAG, new String(data));
        } else if (dataString.contains(">")) {
            stringBuilder.append(dataString);

            command = new String(stringBuilder);
            Log.i(TAG, command);
            newData = true;

        } else {
            stringBuilder.append(dataString);
            Log.i(TAG, new String(data));
        }
        if (newData) {
            Log.i(TAG, command);
            try {
                JSONObject reader = new JSONObject(new String(data));
                commandHandler(reader);
            } catch (JSONException e) {
                Log.e("JSON", "Could not make JSON object");
            }
            newData = false;
        }
    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str+'\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        mReceiveText.append(spn);
    }

    private void commandHandler(JSONObject command){
        if(command.has("states")){
            try{
                JSONArray states = command.getJSONArray("states");
                for (int i = 0; i < states.length(); i++) {
                    try {
                        updateState(states.getJSONObject(i));

                    } catch (JSONException e){

                    }
                }
            } catch (JSONException e){

            }
        }
    }

    private void updateState(JSONObject state){
        for (MotorState mState : motorStates){
            try{
                if (mState.name == state.getString("name")){
                    mState.mSpeed = state.getInt("speed");
                    mState.gate = state.getInt("gate")>0;
                    mState.mDirection = state.getInt("direction")>0;
                    mState.stateTime = state.getInt("time");
                }


            } catch (JSONException e) {

            }
        }
        try{
            MotorState newState = new MotorState(state.getString("name"), state.getInt("speed"),
                    state.getInt("time"), state.getBoolean("direction"), state.getBoolean("gate"));
            motorStates.add(newState);
        } catch (JSONException e){

        }
    }

    public void updateConnectedText(){
        mConnectedText.setText(getString(R.string.connected) + connected.toString());
    }
    private void getStates(){
        send(getString(R.string.get_states_cmd));
    }

    private void getCurrentState(){
        send(getString(R.string.get_current_cmd));
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
        updateConnectedText();
          /*
            Sends the command to get the motor states from the arduino;
             */
        getStates();
        getCurrentState();

    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }

}
