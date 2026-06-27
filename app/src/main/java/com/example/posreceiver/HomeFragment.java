package com.example.posreceiver;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class HomeFragment extends Fragment {

    private TextView tvReceivedTotal;
    private TextView tvSubStatus;
    private Button btnPay;
    private LinearLayout rootLayout;
    
    private static String lastReceivedData = "Waiting for Payment...";
    private static boolean isPaymentReceived = false;

    public static void updateReceivedData(String data) {
        lastReceivedData = data;
        isPaymentReceived = true;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        
        rootLayout = view.findViewById(R.id.home_root);
        tvReceivedTotal = view.findViewById(R.id.tv_received_total_home);
        tvSubStatus = view.findViewById(R.id.tv_sub_status);
        btnPay = view.findViewById(R.id.btn_pay);

        if (isPaymentReceived) {
            setPaymentReceivedMode(lastReceivedData);
        } else {
            resetToHomeMode();
        }

        btnPay.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).sendPaidConfirmation(success -> {
                    if (success) {
                        resetToHomeMode();
                    }
                });
            }
        });

        return view;
    }

    public void setPaymentReceivedMode(String data) {
        lastReceivedData = data;
        isPaymentReceived = true;
        
        if (rootLayout != null) {
            rootLayout.setBackgroundColor(Color.BLACK); 
            tvReceivedTotal.setText(data);
            tvReceivedTotal.setTextColor(Color.parseColor("#4CAF50")); // Green for contrast on black
            tvSubStatus.setText("PAYMENT PENDING CONFIRMATION");
            tvSubStatus.setTextColor(Color.parseColor("#FF5252")); // Redish alert color
            btnPay.setVisibility(View.VISIBLE);
        }
    }

    public void resetToHomeMode() {
        lastReceivedData = "Waiting for Payment...";
        isPaymentReceived = false;
        
        if (rootLayout != null) {
            rootLayout.setBackgroundColor(Color.BLACK);
            tvReceivedTotal.setText(lastReceivedData);
            tvReceivedTotal.setTextColor(Color.WHITE);
            tvSubStatus.setText("Ready to receive from Web POS");
            tvSubStatus.setTextColor(Color.parseColor("#BDBDBD"));
            btnPay.setVisibility(View.GONE);
        }
    }

    public void refreshData(String data) {
        setPaymentReceivedMode(data);
    }
}
