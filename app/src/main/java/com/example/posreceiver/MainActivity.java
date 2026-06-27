package com.example.posreceiver;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import org.json.JSONObject;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothServerSocket serverSocket;
    
    private HomeFragment homeFragment;
    private BluetoothFragment bluetoothFragment;

    // USB Variables
    private UsbManager usbManager;
    private ParcelFileDescriptor usbFileDescriptor;
    private FileInputStream usbInputStream;
    private FileOutputStream usbOutputStream;
    private static final String ACTION_USB_PERMISSION = "com.example.posreceiver.USB_PERMISSION";
    
    private static final String CHANNEL_ID = "pos_receiver_notifications";

    // Active streams for sending PAID confirmation
    private OutputStream activeBluetoothOutputStream;
    private String lastActiveSource = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        setContentView(R.layout.activity_main);
        createNotificationChannel();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        
        homeFragment = new HomeFragment();
        bluetoothFragment = new BluetoothFragment();

        loadFragment(homeFragment);

        View btnHome = findViewById(R.id.btn_nav_home);
        View btnBt = findViewById(R.id.btn_nav_bluetooth);

        btnHome.setOnClickListener(v -> loadFragment(homeFragment));
        btnBt.setOnClickListener(v -> loadFragment(bluetoothFragment));

        checkPermissions();
        startBluetoothServer();
        setupUsbCommunication();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "POS Notifications";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public void showBluetoothFragment() {
        loadFragment(bluetoothFragment);
    }

    private void loadFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
    }

    private void checkPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
        } else {
            permissions.add(Manifest.permission.BLUETOOTH);
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN);
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        List<String> remainingPermissions = new ArrayList<>();
        for (String p : permissions) {
            if (ActivityCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                remainingPermissions.add(p);
            }
        }

        if (!remainingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, remainingPermissions.toArray(new String[0]), 1);
        }
    }

    private void startBluetoothServer() {
        new Thread(() -> {
            try {
                if (bluetoothAdapter != null) {
                    serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("POS-Receiver", MY_UUID);
                    while (true) {
                        BluetoothSocket socket = serverSocket.accept();
                        if (socket != null) {
                            handleConnectedSocket(socket);
                        }
                    }
                }
            } catch (IOException | SecurityException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleConnectedSocket(BluetoothSocket socket) {
        new Thread(() -> {
            try {
                InputStream inputStream = socket.getInputStream();
                activeBluetoothOutputStream = socket.getOutputStream();
                readDataFromStream(inputStream, "Bluetooth");
            } catch (IOException e) {
                e.printStackTrace();
                activeBluetoothOutputStream = null;
            }
        }).start();
    }

    private void setupUsbCommunication() {
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(usbReceiver, filter);
        }

        UsbAccessory[] accessories = usbManager.getAccessoryList();
        if (accessories != null && accessories.length > 0) {
            openUsbAccessory(accessories[0]);
        }
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbAccessory accessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (accessory != null) openUsbAccessory(accessory);
                    }
                }
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                closeUsbAccessory();
            }
        }
    };

    private void openUsbAccessory(UsbAccessory accessory) {
        usbFileDescriptor = usbManager.openAccessory(accessory);
        if (usbFileDescriptor != null) {
            FileDescriptor fd = usbFileDescriptor.getFileDescriptor();
            usbInputStream = new FileInputStream(fd);
            usbOutputStream = new FileOutputStream(fd);
            new Thread(() -> readDataFromStream(usbInputStream, "USB")).start();
            Toast.makeText(this, "USB Accessory Connected", Toast.LENGTH_SHORT).show();
        }
    }

    private void closeUsbAccessory() {
        try {
            if (usbFileDescriptor != null) usbFileDescriptor.close();
            if (usbInputStream != null) usbInputStream.close();
            if (usbOutputStream != null) usbOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            usbFileDescriptor = null;
            usbInputStream = null;
            usbOutputStream = null;
        }
    }

    private void readDataFromStream(InputStream inputStream, String source) {
        byte[] buffer = new byte[1024];
        int bytes;
        try {
            while ((bytes = inputStream.read(buffer)) != -1) {
                String received = new String(buffer, 0, bytes);
                processReceivedData(received, source);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processReceivedData(String data, String source) {
        try {
            JSONObject json = new JSONObject(data);
            int total = json.getInt("total");
            String displayData = "Total Amount: " + total + " ৳";
            lastActiveSource = source;
            
            handler.post(() -> {
                wakeScreen();
                
                // Show notification sound/banner
                showDataNotification(displayData, source);
                
                if (homeFragment != null && homeFragment.isVisible()) {
                    homeFragment.setPaymentReceivedMode(displayData);
                } else {
                    loadFragment(homeFragment);
                    homeFragment.setPaymentReceivedMode(displayData);
                }
            });
        } catch (Exception e) {
            String displayData = "Received: " + data;
            lastActiveSource = source;
            handler.post(() -> {
                wakeScreen();
                showDataNotification(displayData, source);
                
                if (homeFragment != null && homeFragment.isVisible()) {
                    homeFragment.setPaymentReceivedMode(displayData);
                } else {
                    loadFragment(homeFragment);
                    homeFragment.setPaymentReceivedMode(displayData);
                }
            });
        }
    }

    public interface ConfirmationCallback {
        void onResult(boolean success);
    }

    public void sendPaidConfirmation(ConfirmationCallback callback) {
        new Thread(() -> {
            boolean success = false;
            try {
                String msg = "PAID\n";
                if ("USB".equals(lastActiveSource) && usbOutputStream != null) {
                    usbOutputStream.write(msg.getBytes());
                    usbOutputStream.flush();
                    success = true;
                } else if ("Bluetooth".equals(lastActiveSource) && activeBluetoothOutputStream != null) {
                    activeBluetoothOutputStream.write(msg.getBytes());
                    activeBluetoothOutputStream.flush();
                    success = true;
                }
                
                final boolean finalSuccess = success;
                handler.post(() -> {
                    if (finalSuccess) {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Status")
                                .setMessage("Paid")
                                .setPositiveButton("OK", null)
                                .show();
                    } else {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Status")
                                .setMessage("Failed to send signal")
                                .setPositiveButton("OK", null)
                                .show();
                    }
                    if (callback != null) callback.onResult(finalSuccess);
                });
            } catch (IOException e) {
                e.printStackTrace();
                handler.post(() -> {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Status")
                            .setMessage("Failed to send signal")
                            .setPositiveButton("OK", null)
                            .show();
                    if (callback != null) callback.onResult(false);
                });
            }
        }).start();
    }

    private void wakeScreen() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK |
                PowerManager.ACQUIRE_CAUSES_WAKEUP |
                PowerManager.ON_AFTER_RELEASE, "POSReceiver:WakeLock");
        wakeLock.acquire(3000);
    }

    private void showDataNotification(String message, String source) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("New Payment Amount Received")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setSound(alarmSound)
                .setDefaults(Notification.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(1, builder.build());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        loadFragment(homeFragment);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(usbReceiver); } catch (Exception ignored) {}
        closeUsbAccessory();
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException e) { e.printStackTrace(); }
    }
}
