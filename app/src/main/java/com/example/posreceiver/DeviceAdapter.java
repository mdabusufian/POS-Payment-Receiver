package com.example.posreceiver;

import android.bluetooth.BluetoothDevice;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {

    private List<BluetoothDevice> devices = new ArrayList<>();
    private OnDeviceClickListener listener;
    private boolean isPaired;

    public interface OnDeviceClickListener {
        void onConnectClick(BluetoothDevice device);
        void onForgetClick(BluetoothDevice device);
    }

    public DeviceAdapter(boolean isPaired, OnDeviceClickListener listener) {
        this.isPaired = isPaired;
        this.listener = listener;
    }

    public void updateDevices(List<BluetoothDevice> newDevices) {
        this.devices = newDevices;
        notifyDataSetChanged();
    }

    public void addDevice(BluetoothDevice device) {
        if (!devices.contains(device)) {
            devices.add(device);
            notifyItemInserted(devices.size() - 1);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_device, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BluetoothDevice device = devices.get(position);
        try {
            holder.tvName.setText(device.getName() != null ? device.getName() : "Unknown Device");
            holder.tvAddress.setText(device.getAddress());
            
            boolean isConnected = isConnected(device);

            if (isConnected) {
                holder.btnAction.setText("Connected");
                holder.btnAction.setTextColor(Color.parseColor("#4CAF50")); // Green
                holder.btnAction.setEnabled(false);
            } else {
                holder.btnAction.setText(isPaired ? "Connect" : "Pair");
                holder.btnAction.setTextColor(Color.parseColor("#2196F3")); // Default Blue-ish
                holder.btnAction.setEnabled(true);
            }

            holder.btnForget.setVisibility(isPaired ? View.VISIBLE : View.GONE);

            holder.btnAction.setOnClickListener(v -> listener.onConnectClick(device));
            holder.btnForget.setOnClickListener(v -> listener.onForgetClick(device));
        } catch (SecurityException e) {
            holder.tvName.setText("Permission Denied");
        }
    }

    private boolean isConnected(BluetoothDevice device) {
        try {
            Method m = device.getClass().getMethod("isConnected", (Class[]) null);
            return (boolean) m.invoke(device, (Object[]) null);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvAddress;
        Button btnAction, btnForget;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_device_name);
            tvAddress = itemView.findViewById(R.id.tv_device_address);
            btnAction = itemView.findViewById(R.id.btn_action);
            btnForget = itemView.findViewById(R.id.btn_forget);
        }
    }
}
