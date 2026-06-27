package com.example.posreceiver;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BluetoothFragment extends Fragment {

    private TextView tvStatus;
    private SwitchCompat swBluetoothToggle;
    private BluetoothAdapter bluetoothAdapter;
    private DeviceAdapter pairedAdapter;
    private DeviceAdapter availableAdapter;
    private List<BluetoothDevice> availableDevicesList = new ArrayList<>();
    private Handler handler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_bluetooth, container, false);

        tvStatus = view.findViewById(R.id.tv_status_bt);
        swBluetoothToggle = view.findViewById(R.id.sw_bluetooth_toggle);
        Button btnScan = view.findViewById(R.id.btn_scan);
        RecyclerView rvPaired = view.findViewById(R.id.rv_paired_devices);
        RecyclerView rvAvailable = view.findViewById(R.id.rv_available_devices);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        setupRecyclerViews(rvPaired, rvAvailable);

        if (bluetoothAdapter != null) {
            swBluetoothToggle.setChecked(bluetoothAdapter.isEnabled());
            swBluetoothToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                try {
                    if (isChecked) {
                        if (!bluetoothAdapter.isEnabled()) {
                            if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                bluetoothAdapter.enable();
                                tvStatus.setText("Status: Turning Bluetooth ON...");
                            }
                        }
                    } else {
                        if (bluetoothAdapter.isEnabled()) {
                            if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                bluetoothAdapter.disable();
                                tvStatus.setText("Status: Turning Bluetooth OFF...");
                            }
                        }
                    }
                } catch (SecurityException e) {
                    Toast.makeText(getContext(), "Permission denied", Toast.LENGTH_SHORT).show();
                    swBluetoothToggle.setChecked(bluetoothAdapter.isEnabled());
                }
            });
        }

        btnScan.setOnClickListener(v -> {
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                checkPermissionsAndScan();
            } else {
                Toast.makeText(getContext(), "Please enable Bluetooth first", Toast.LENGTH_SHORT).show();
            }
        });

        checkPermissions();
        updatePairedDevices();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction("android.bluetooth.device.action.ACL_CONNECTED");
        filter.addAction("android.bluetooth.device.action.ACL_DISCONNECTED");
        
        if (getActivity() != null) {
            getActivity().registerReceiver(receiver, filter);
        }

        return view;
    }

    private void setupRecyclerViews(RecyclerView rvPaired, RecyclerView rvAvailable) {
        pairedAdapter = new DeviceAdapter(true, new DeviceAdapter.OnDeviceClickListener() {
            @Override
            public void onConnectClick(BluetoothDevice device) {
                Toast.makeText(getContext(), "Ready for data transfer", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onForgetClick(BluetoothDevice device) {
                unpairDevice(device);
            }
        });

        availableAdapter = new DeviceAdapter(false, new DeviceAdapter.OnDeviceClickListener() {
            @Override
            public void onConnectClick(BluetoothDevice device) {
                pairDevice(device);
            }

            @Override
            public void onForgetClick(BluetoothDevice device) {}
        });

        rvPaired.setLayoutManager(new LinearLayoutManager(getContext()));
        rvPaired.setAdapter(pairedAdapter);

        rvAvailable.setLayoutManager(new LinearLayoutManager(getContext()));
        rvAvailable.setAdapter(availableAdapter);
    }

    private void checkPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
        }

        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String p : permissions) {
            if (ActivityCompat.checkSelfPermission(getContext(), p) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(p);
            }
        }

        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(getActivity(), listPermissionsNeeded.toArray(new String[0]), 1);
        }
    }

    private void checkPermissionsAndScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.BLUETOOTH_SCAN}, 1);
                return;
            }
        } else {
            if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                return;
            }
        }
        startDiscovery();
    }

    private void updatePairedDevices() {
        if (bluetoothAdapter == null) return;
        try {
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            pairedAdapter.updateDevices(new ArrayList<>(pairedDevices));
        } catch (SecurityException e) {
            tvStatus.setText("Error: Permission denied");
        }
    }

    private void startDiscovery() {
        try {
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
            availableDevicesList.clear();
            availableAdapter.updateDevices(availableDevicesList);
            
            if (bluetoothAdapter.startDiscovery()) {
                tvStatus.setText("Status: Scanning...");
            } else {
                tvStatus.setText("Status: Scan failed. Restarting BT may help.");
            }
        } catch (SecurityException e) {
            tvStatus.setText("Error: Permission denied for scan");
        }
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    availableAdapter.addDevice(device);
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                tvStatus.setText("Status: Scanning...");
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                tvStatus.setText("Status: Scan Finished");
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                handleStateChange(state);
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                updatePairedDevices();
            } else if ("android.bluetooth.device.action.ACL_CONNECTED".equals(action) || 
                       "android.bluetooth.device.action.ACL_DISCONNECTED".equals(action)) {
                updatePairedDevices();
                availableAdapter.notifyDataSetChanged();
            }
        }
    };

    private void handleStateChange(int state) {
        if (state == BluetoothAdapter.STATE_ON) {
            swBluetoothToggle.setChecked(true);
            tvStatus.setText("Status: Bluetooth ON");
            updatePairedDevices();
        } else if (state == BluetoothAdapter.STATE_OFF) {
            swBluetoothToggle.setChecked(false);
            tvStatus.setText("Status: Bluetooth OFF");
            pairedAdapter.updateDevices(new ArrayList<>());
            availableAdapter.updateDevices(new ArrayList<>());
        }
    }

    private void pairDevice(BluetoothDevice device) {
        try {
            device.createBond();
            tvStatus.setText("Status: Pairing...");
        } catch (SecurityException e) {
            tvStatus.setText("Error: Permission denied");
        }
    }

    private void unpairDevice(BluetoothDevice device) {
        try {
            Method m = device.getClass().getMethod("removeBond", (Class[]) null);
            m.invoke(device, (Object[]) null);
            handler.postDelayed(this::updatePairedDevices, 1000);
        } catch (Exception e) {
            tvStatus.setText("Error: Failed to unpair");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (getActivity() != null) {
                getActivity().unregisterReceiver(receiver);
            }
        } catch (Exception ignored) {}
    }
}
