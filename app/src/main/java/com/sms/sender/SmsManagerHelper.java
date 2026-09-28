package com.sms.sender;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.List;

/**
 * Validates preconditions and sends an SMS through the specified SIM subscription.
 *
 * Uses Android's native SmsManager with sent/delivery PendingIntents so the result
 * of each send attempt can be logged.
 */
public class SmsManagerHelper {

    private static final String TAG = "SmsManagerHelper";

    private static final String ACTION_SMS_SENT     = "com.sms.sender.SMS_SENT";
    private static final String ACTION_SMS_DELIVERED = "com.sms.sender.SMS_DELIVERED";

    /**
     * Validates all preconditions, then sends the SMS.
     *
     * @param context        Application context
     * @param subscriptionId Subscription ID of the SIM selected by the user
     * @param number         Destination phone number
     * @param text           Message body
     */
    public static void sendSms(Context context, int subscriptionId,
                               String number, String text) {

        // 1. Check SEND_SMS permission
        if (ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "SEND_SMS permission not granted — aborting.");
            return;
        }

        // 2. Verify the subscription still exists
        if (!isSubscriptionValid(context, subscriptionId)) {
            Log.e(TAG, "Subscription ID " + subscriptionId
                    + " is no longer active — aborting.");
            return;
        }

        // 3. Build sent / delivery PendingIntents for result callbacks
        Intent sentIntent = new Intent(ACTION_SMS_SENT);
        Intent deliveredIntent = new Intent(ACTION_SMS_DELIVERED);

        int flags = PendingIntent.FLAG_ONE_SHOT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent sentPI = PendingIntent.getBroadcast(
                context, 0, sentIntent, flags);
        PendingIntent deliveredPI = PendingIntent.getBroadcast(
                context, 0, deliveredIntent, flags);

        // 4. Register one-shot broadcast receivers for the result
        registerSentReceiver(context);
        registerDeliveredReceiver(context);

        // 5. Get SmsManager for the selected SIM and send
        try {
            SmsManager smsManager = SmsManager.getSmsManagerForSubscriptionId(subscriptionId);

            // splitMessage handles texts longer than 160 chars automatically
            if (text.length() > 160) {
                java.util.ArrayList<String> parts = smsManager.divideMessage(text);
                java.util.ArrayList<PendingIntent> sentPIs = new java.util.ArrayList<>();
                java.util.ArrayList<PendingIntent> deliveredPIs = new java.util.ArrayList<>();
                for (int i = 0; i < parts.size(); i++) {
                    sentPIs.add(sentPI);
                    deliveredPIs.add(deliveredPI);
                }
                smsManager.sendMultipartTextMessage(number, null, parts, sentPIs, deliveredPIs);
            } else {
                smsManager.sendTextMessage(number, null, text, sentPI, deliveredPI);
            }

            Log.d(TAG, "SMS send initiated to: " + number);

        } catch (Exception e) {
            Log.e(TAG, "Exception while sending SMS: " + e.getMessage(), e);
        }
    }

    // ───────────────────────────────────────────────
    // Subscription validation
    // ───────────────────────────────────────────────

    private static boolean isSubscriptionValid(Context context, int subscriptionId) {
        if (subscriptionId < 0) return false;

        if (ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            // Cannot verify — allow the attempt; SmsManager will fail gracefully if invalid
            Log.w(TAG, "READ_PHONE_STATE not granted; skipping subscription validation.");
            return true;
        }

        SubscriptionManager sm =
                (SubscriptionManager) context.getSystemService(
                        Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        if (sm == null) return false;

        List<SubscriptionInfo> list = sm.getActiveSubscriptionInfoList();
        if (list == null) return false;

        for (SubscriptionInfo info : list) {
            if (info.getSubscriptionId() == subscriptionId) return true;
        }
        return false;
    }

    // ───────────────────────────────────────────────
    // Result broadcast receivers
    // ───────────────────────────────────────────────

    private static void registerSentReceiver(Context context) {
        BroadcastReceiver sentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                switch (getResultCode()) {
                    case android.app.Activity.RESULT_OK:
                        Log.d(TAG, "SMS send initiated successfully.");
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        Log.e(TAG, "SMS send failed: generic failure.");
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        Log.e(TAG, "SMS send failed: no service.");
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                        Log.e(TAG, "SMS send failed: null PDU.");
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        Log.e(TAG, "SMS send failed: radio off.");
                        break;
                    default:
                        Log.e(TAG, "SMS send failed: result code " + getResultCode());
                        break;
                }
                try {
                    ctx.unregisterReceiver(this);
                } catch (IllegalArgumentException ignored) {}
            }
        };

        IntentFilter filter = new IntentFilter(ACTION_SMS_SENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(sentReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(sentReceiver, filter);
        }
    }

    private static void registerDeliveredReceiver(Context context) {
        BroadcastReceiver deliveredReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (getResultCode() == android.app.Activity.RESULT_OK) {
                    Log.d(TAG, "SMS delivered to recipient.");
                } else {
                    Log.w(TAG, "SMS delivery pending or failed: result code " + getResultCode());
                }
                try {
                    ctx.unregisterReceiver(this);
                } catch (IllegalArgumentException ignored) {}
            }
        };

        IntentFilter filter = new IntentFilter(ACTION_SMS_DELIVERED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(deliveredReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(deliveredReceiver, filter);
        }
    }
}
