package com.sms.sender;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * Receives FCM data messages and delegates to SmsManagerHelper.
 *
 * No notification is created. No Activity is launched. No UI is shown.
 * The service processes the message and sends the SMS silently.
 */
public class SmsFirebaseService extends FirebaseMessagingService {

    private static final String TAG = "SmsFirebaseService";

    // ───────────────────────────────────────────────
    // FCM message received
    // ───────────────────────────────────────────────

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Map<String, String> data = remoteMessage.getData();

        if (data.isEmpty()) {
            Log.w(TAG, "Received FCM message with no data payload — ignoring.");
            return;
        }

        String number = data.get("number");
        String text = data.get("text");

        // Validate
        if (number == null || number.trim().isEmpty()) {
            Log.e(TAG, "FCM payload missing 'number' field.");
            return;
        }
        if (text == null || text.trim().isEmpty()) {
            Log.e(TAG, "FCM payload missing 'text' field.");
            return;
        }

        number = number.trim();
        text = text.trim();

        // Retrieve saved subscription ID
        SharedPreferences prefs = getSharedPreferences(
                MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        int subscriptionId = prefs.getInt(MainActivity.KEY_SUBSCRIPTION_ID, -1);

        if (subscriptionId == -1) {
            Log.e(TAG, "No SIM selected. Open the app and select a SIM first.");
            return;
        }

        Log.d(TAG, "FCM command received. Sending SMS to: " + number
                + " via subscriptionId: " + subscriptionId);

        // Delegate to helper
        SmsManagerHelper.sendSms(this, subscriptionId, number, text);
    }

    // ───────────────────────────────────────────────
    // FCM token refresh
    // ───────────────────────────────────────────────

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "FCM token refreshed — updating Firestore.");
        MainActivity.saveTokenToFirestore(token);
    }
}
