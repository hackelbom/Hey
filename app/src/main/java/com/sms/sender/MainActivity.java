package com.sms.sender;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    static final String PREFS_NAME = "SmsSenderPrefs";
    static final String KEY_SUBSCRIPTION_ID = "selectedSubscriptionId";

    private static final int REQUEST_SMS_PERMISSION = 1001;
    private static final int REQUEST_READ_PHONE_STATE = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // No layout — minimal UI via dialogs only

        requestRequiredPermissions();
    }

    // ───────────────────────────────────────────────
    // Permissions
    // ───────────────────────────────────────────────

    private void requestRequiredPermissions() {
        boolean hasSms = ContextCompat.checkSelfPermission(this,
                Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
        boolean hasPhone = ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;

        if (!hasSms || !hasPhone) {
            String[] perms;
            if (!hasSms && !hasPhone) {
                perms = new String[]{Manifest.permission.SEND_SMS,
                        Manifest.permission.READ_PHONE_STATE};
            } else if (!hasSms) {
                perms = new String[]{Manifest.permission.SEND_SMS};
            } else {
                perms = new String[]{Manifest.permission.READ_PHONE_STATE};
            }
            ActivityCompat.requestPermissions(this, perms, REQUEST_SMS_PERMISSION);
        } else {
            onPermissionsReady();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            onPermissionsReady();
        } else {
            showToast("SMS permission is required for this app to work.");
            // Keep the activity open so the user can understand the state
        }
    }

    // ───────────────────────────────────────────────
    // After permissions granted
    // ───────────────────────────────────────────────

    private void onPermissionsReady() {
        registerFcmToken();
        showSimSelectionDialog();
    }

    // ───────────────────────────────────────────────
    // FCM token registration
    // ───────────────────────────────────────────────

    private void registerFcmToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        showToast("FCM token fetch failed.");
                        return;
                    }
                    String token = task.getResult();
                    saveTokenToFirestore(token);
                });
    }

    static void saveTokenToFirestore(String token) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        CollectionReference devices = db.collection("devices");

        Map<String, Object> data = new HashMap<>();
        data.put("fcmToken", token);

        // Use a fixed document ID per device. A simple approach is to
        // use the token itself as the document ID, or a fixed "device" doc.
        // We use a fixed doc "device_001" for simplicity as per the spec.
        devices.document("device_001")
                .set(data)
                .addOnCompleteListener(task -> {
                    // Silent — no UI feedback needed for token storage
                });
    }

    // ───────────────────────────────────────────────
    // SIM selection
    // ───────────────────────────────────────────────

    private void showSimSelectionDialog() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            showToast("Phone state permission needed to list SIMs.");
            return;
        }

        SubscriptionManager subscriptionManager =
                (SubscriptionManager) getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);

        if (subscriptionManager == null) {
            showToast("SubscriptionManager not available on this device.");
            return;
        }

        List<SubscriptionInfo> subscriptions = subscriptionManager.getActiveSubscriptionInfoList();

        if (subscriptions == null || subscriptions.isEmpty()) {
            showToast("No active SIM found.");
            return;
        }

        // Build label list for the dialog
        String[] simLabels = new String[subscriptions.size()];
        int[] subscriptionIds = new int[subscriptions.size()];

        for (int i = 0; i < subscriptions.size(); i++) {
            SubscriptionInfo info = subscriptions.get(i);
            subscriptionIds[i] = info.getSubscriptionId();
            CharSequence carrierName = info.getCarrierName();
            String displayName = (info.getDisplayName() != null)
                    ? info.getDisplayName().toString() : "SIM " + (i + 1);
            String carrier = (carrierName != null && carrierName.length() > 0)
                    ? " (" + carrierName + ")" : "";
            simLabels[i] = "SIM " + (i + 1) + ": " + displayName + carrier;
        }

        // Find currently saved SIM to pre-select
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int savedId = prefs.getInt(KEY_SUBSCRIPTION_ID, -1);
        int checkedItem = -1;
        for (int i = 0; i < subscriptionIds.length; i++) {
            if (subscriptionIds[i] == savedId) {
                checkedItem = i;
                break;
            }
        }

        final int[] finalSubscriptionIds = subscriptionIds;
        final int preChecked = checkedItem;

        new AlertDialog.Builder(this)
                .setTitle("Select SIM for outgoing SMS")
                .setSingleChoiceItems(simLabels, preChecked, (dialog, which) -> {
                    int selectedId = finalSubscriptionIds[which];
                    prefs.edit().putInt(KEY_SUBSCRIPTION_ID, selectedId).apply();
                    showToast("SIM selected. Subscription ID: " + selectedId);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
