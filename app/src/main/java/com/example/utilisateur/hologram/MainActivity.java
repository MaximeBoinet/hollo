package com.example.utilisateur.hologram;

import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.speech.RecognizerIntent;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

import static android.bluetooth.BluetoothAdapter.STATE_CONNECTED;
import static android.content.ContentValues.TAG;

public class MainActivity extends Activity {

    UUID service_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    UUID RX_char_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    UUID TX_char_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");

    UUID UUID_notif = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private ImageButton btnSpeak;
    private ImageButton nextVid;
    private ImageButton prevVid;
    private VideoView videoView;
    ArrayList<Integer> videos = new ArrayList<>();
    int currentVid = -1;
    private final int REQ_CODE_SPEECH_INPUT = 100;

    private static final int REQUEST_ENABLE_BT = 5;
    BluetoothAdapter bluetoothAdapter;
    BluetoothDevice myDevice;
    BluetoothGatt gatt;
    MyAdapter adapter;
    BluetoothManager bluetoothManager;
    BluetoothAdapter.LeScanCallback scanCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnSpeak = findViewById(R.id.btnSpeak);
        videoView = findViewById(R.id.videoView);
        nextVid = findViewById(R.id.nextVid);
        prevVid = findViewById(R.id.prevVid);
        videos.add(Integer.valueOf(R.raw.flamme));
        videos.add(Integer.valueOf(R.raw.fenetre1));


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
        }

        scanCallback = new BluetoothAdapter.LeScanCallback() {
                        @Override
                        public void onLeScan(BluetoothDevice bluetoothDevice, int i, byte[] bytes) {
                            Log.i(TAG, "onLeScan: "+bluetoothDevice);
                            if (bluetoothDevice != null && bluetoothDevice.getName() != null && bluetoothDevice.getName().equals("MyHologramCommand")) {
                                handleDeviceFound(bluetoothDevice);
                            }
                        }};

        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }


        bluetoothAdapter.startLeScan(scanCallback);

        nextVid.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getNextVideo();
            }
        });
        prevVid.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getPreviousVideo();
            }
        });
        btnSpeak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptSpeechInput();
            }
        });

    }

    @Override
    protected void onStop() {
        super.onStop();
        gatt.disconnect();
    }

    private void getNextVideo() {
        currentVid += 1;
        if  (currentVid >= videos.size()) {
            currentVid = 0;
        }
        playVideo(Uri.parse("android.resource://com.example.utilisateur.hologram/"+videos.get(currentVid)));
    }

    private void getPreviousVideo() {
        currentVid -= 1;
        if  (currentVid < 0) {
            currentVid = videos.size()-1;
        }
        playVideo(Uri.parse("android.resource://com.example.utilisateur.hologram/"+videos.get(currentVid)));
    }

    private void handleDeviceFound(BluetoothDevice bluetoothDevice) {
        myDevice = bluetoothDevice;
        bluetoothAdapter.stopLeScan(scanCallback);
        communicate();
    }

    private void playVideo(Uri videoUri) {
        MediaController mediaController = new MediaController(this);
        mediaController.setAnchorView(videoView);
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.setLooping(true);
            }
        });
        videoView.setMediaController(mediaController);
        videoView.setVideoURI(videoUri);
        videoView.start();
    }

    private void promptSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.speech_prompt));
        try {
            sendData(1);
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(), getString(R.string.speech_not_supported), Toast.LENGTH_SHORT).show();
        }
    }

    private boolean sendData(int val) {
        if (gatt == null) {
            Log.e("error", "lost connection");
        }
        BluetoothGattService Service = gatt.getService(service_UUID);
        if (Service == null) {
            Log.e("error", "service not found!");
        }
        BluetoothGattCharacteristic charac = Service.getCharacteristic(TX_char_UUID);
        if (charac == null) {
            Log.e("", "char not found!");
        }

        byte[] value = new byte[1];

        value[0] = (byte) (val & 0xFF);
        charac.setValue(value);

        return gatt.writeCharacteristic(charac);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        sendData(0);
        switch (requestCode) {
            case REQ_CODE_SPEECH_INPUT: {
                if (resultCode == RESULT_OK && null != data) {

                    ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    Uri videoUri = null;
                    if (result.get(0).contains("1") ) {
                        videoUri = Uri.parse("android.resource://com.example.utilisateur.hologram/" + R.raw.flamme + "");
                    } else if (result.get(0).contains("2"))
                        videoUri = Uri.parse("android.resource://com.example.utilisateur.hologram/" + R.raw.fenetre1 + "");
                    playVideo(videoUri);
                }
                break;
            }

        }
    }

    public void doItOnUIPrev() {
        runOnUiThread(new Runnable() {
            public void run() {
                getPreviousVideo();
            }
        });
    }

    public void doItOnUINext() {
        runOnUiThread(new Runnable() {
            public void run() {
                getNextVideo();
            }
        });
    }

    public void doItOnUIMic() {
        runOnUiThread(new Runnable() {
            public void run() {
                promptSpeechInput();
            }
        });
    }
    public void communicate() {
        BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                if (newState == STATE_CONNECTED){
                    gatt.discoverServices();
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);
                BluetoothGattCharacteristic characteristic = gatt.getService(service_UUID).getCharacteristic(RX_char_UUID);
                gatt.setCharacteristicNotification(characteristic, true);
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID_notif);
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
            }

            @Override
            public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorRead(gatt, descriptor, status);
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorWrite(gatt, descriptor, status);
                BluetoothGattCharacteristic characteristic = gatt.getService(service_UUID).getCharacteristic(RX_char_UUID);
                gatt.setCharacteristicNotification(characteristic, true);
                characteristic.setValue(new byte[]{1, 1});
                gatt.writeCharacteristic(characteristic);
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                short val = (short)(characteristic.getValue()[0] & 0xFF);
                Log.d("CharChanged", String.valueOf(val));
                if (val == 0) {
                    doItOnUIPrev();
                } else if (val == 1) {
                    doItOnUINext();
                } else if (val == 2) {
                    doItOnUIMic();
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicWrite(gatt, characteristic, status);
                Log.d("CharWrite", String.valueOf(characteristic.getValue()[0] & 0xFF));

            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicRead(gatt, characteristic, status);
                Log.d("CharRead", String.valueOf(characteristic.getValue()[0] & 0xFF));

            }
        };
        Log.d(TAG, "communicate: connection");
        gatt = myDevice.connectGatt(this, false, gattCallback);
        if (gatt.connect()) {
            Log.i(TAG, "Connection: managed connection");
        } else {
            Log.e(TAG, "Fail: failed to connect");
        }
    }
}
