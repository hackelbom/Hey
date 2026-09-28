package com.sms.sender

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.view.ViewGroup
import android.widget.*
import android.text.InputType

class MainActivity : Activity() {
    private lateinit var simSpinner: Spinner
    private lateinit var numberInput: EditText
    private lateinit var messageInput: EditText
    private var subscriptions = emptyList<android.telephony.SubscriptionInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 48, 36, 24)
        }
        root.addView(TextView(this).apply { text = "SMS Sender"; textSize = 26f })
        root.addView(TextView(this).apply { text = "Choose SIM" })
        simSpinner = Spinner(this)
        root.addView(simSpinner)
        numberInput = EditText(this).apply {
            hint = "Target phone number"
            inputType = InputType.TYPE_CLASS_PHONE
        }
        root.addView(numberInput)
        messageInput = EditText(this).apply {
            hint = "Message"
            minLines = 4
            gravity = android.view.Gravity.TOP
        }
        root.addView(messageInput)
        val send = Button(this).apply { text = "Review and Send SMS" }
        root.addView(send)
        val note = TextView(this).apply {
            text = "SMS is sent only after you tap the button and confirm. Firebase database path and data format must be configured separately."
            textSize = 13f
        }
        root.addView(note)
        setContentView(root)

        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE), 100)
        } else loadSims()

        send.setOnClickListener { confirmAndSend() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 100 && results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }) loadSims()
        else Toast.makeText(this, "SMS and phone permissions are required.", Toast.LENGTH_LONG).show()
    }

    private fun loadSims() {
        try {
            val manager = getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            subscriptions = manager.activeSubscriptionInfoList ?: emptyList()
            val labels = if (subscriptions.isEmpty()) listOf("No active SIM found") else
                subscriptions.mapIndexed { index, info ->
                    "SIM ${index + 1} — ${info.carrierName} (${info.number.ifBlank { "number hidden" }})"
                }
            simSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        } catch (_: Exception) {
            simSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("SIM info unavailable"))
        }
    }

    private fun confirmAndSend() {
        val number = numberInput.text.toString().trim()
        val message = messageInput.text.toString().trim()
        if (number.isBlank() || message.isBlank()) {
            Toast.makeText(this, "Enter a phone number and message.", Toast.LENGTH_SHORT).show()
            return
        }
        if (subscriptions.isEmpty()) {
            Toast.makeText(this, "No active SIM available or permission missing.", Toast.LENGTH_LONG).show()
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Confirm SMS")
            .setMessage("Send this message to $number using ${simSpinner.selectedItem}?\n\n$message")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Send") { _, _ ->
                try {
                    val subId = subscriptions[simSpinner.selectedItemPosition].subscriptionId
                    val sms = SmsManager.getSmsManagerForSubscriptionId(subId)
                    val parts = sms.divideMessage(message)
                    sms.sendMultipartTextMessage(number, null, parts, null, null)
                    Toast.makeText(this, "SMS submitted to Android SMS service.", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not send SMS: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }.show()
    }
}
